package org.unipod.unipoderpbackend.service.improvement;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.improvement.AskForChangesRequest;
import org.unipod.unipoderpbackend.dto.improvement.RefinementRequestResponse;
import org.unipod.unipoderpbackend.dto.improvement.RequestedChangeItem;
import org.unipod.unipoderpbackend.dto.submission.CaseTransitionRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequestedChange;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.CaseStage;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.CaseFileService;

/**
 * "Ask for changes" on a brief. The stage change itself always goes through Waluube's
 * {@link CaseFileService#transition}, which joins this transaction: if the refinement rows, the audit
 * entry or the notification fail, the stage change, its history row and its audit entry roll back too.
 */
@Service
public class RefinementRequestService {
    static final String ASK_ROLES = "hasAnyRole('FACT_FINDING_OFFICER', 'INNOVATION_COORDINATOR', 'ADMINISTRATOR')";
    static final int MAX_GUIDANCE_LENGTH = 2000;
    static final int MAX_CHANGES = 20;
    static final int MAX_CHANGE_LENGTH = 500;
    static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");
    static final String REFINEMENT_EVENT = "submission.refinement_requested";

    static final String ALREADY_OPEN = "An improvement request is already open for this brief.";
    static final String STAGE_NOT_ALLOWED = "Improvement can only be requested while the brief is in fact-finding, "
            + "ready for review, in review, or already marked for improvement.";
    static final String CHANGED = "This brief was changed by another user. Reload it and try again.";
    private static final String CYCLE_CONSTRAINT = "uq_refinement_requests_brief_cycle";

    private final SubmissionRepository submissions;
    private final RefinementRequestRepository requests;
    private final RefinementRequestedChangeRepository changes;
    private final CaseFileService caseFiles;
    private final ImprovementAudit audit;
    private final NotificationOutbox outbox;
    private final Clock clock;

    public RefinementRequestService(
            SubmissionRepository submissions,
            RefinementRequestRepository requests,
            RefinementRequestedChangeRepository changes,
            CaseFileService caseFiles,
            ImprovementAudit audit,
            NotificationOutbox outbox,
            Clock clock
    ) {
        this.submissions = submissions;
        this.requests = requests;
        this.changes = changes;
        this.caseFiles = caseFiles;
        this.audit = audit;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize(ASK_ROLES)
    public RefinementRequestResponse ask(UUID briefId, AskForChangesRequest request, Authentication authentication) {
        Caller caller = requireCaller(authentication);
        Submission brief = submissions.findByIdForUpdate(briefId)
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));

        String guidance = guidanceNote(request.guidanceNote());
        LocalDate dueDate = dueDate(request.dueDate());
        List<String> requested = requestedChanges(request.requestedChanges());

        boolean adopted = switch (brief.getStage()) {
            case IMPROVEMENT_REQUESTED -> {
                if (requests.existsByBriefIdAndStatus(briefId, RefinementStatus.OPEN)) {
                    throw new SubmissionConflictException(ALREADY_OPEN);
                }
                yield true;
            }
            case IN_FACT_FINDING, READY_FOR_REVIEW, IN_REVIEW -> {
                if (brief.getStage() != SubmissionStage.IN_FACT_FINDING && !caller.operator()) {
                    throw new AccessDeniedException("Only a coordinator or administrator can ask for changes once "
                            + "fact-finding is complete.");
                }
                if (request.caseVersion() == null) {
                    throw unprocessable("caseVersion is required to move the brief to improvement requested.");
                }
                // Waluube's transition: version check, roles, history row and audit entry; joins this transaction.
                caseFiles.transition(briefId, new CaseTransitionRequest(
                        CaseStage.IMPROVEMENT_REQUESTED, guidance, request.caseVersion()), authentication);
                yield false;
            }
            default -> throw new SubmissionConflictException(STAGE_NOT_ALLOWED);
        };

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int cycle = requests.findTopByBriefIdOrderByCycleDesc(briefId)
                .map(previous -> previous.getCycle() + 1)
                .orElse(1);
        RefinementSource source = caller.officer() ? RefinementSource.FACT_FINDING : RefinementSource.COORDINATOR;
        RefinementRequest saved = persist(() -> requests.saveAndFlush(new RefinementRequest(
                briefId, cycle, source, guidance, dueDate, authentication.getName(), now)));
        List<RefinementRequestedChange> savedChanges = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            savedChanges.add(new RefinementRequestedChange(saved.getId(), i + 1, requested.get(i)));
        }
        List<RefinementRequestedChange> stored = persist(() -> changes.saveAllAndFlush(savedChanges));

        // Structural values only: no guidance note and no requested-change text (see ImprovementAudit).
        audit.improvementRequested(authentication.getName(), saved, stored.size(), adopted);
        notifySubmitter(brief, saved, authentication.getName(), now);

        return new RefinementRequestResponse(
                saved.getId(),
                saved.getBriefId(),
                saved.getCycle(),
                saved.getSource(),
                saved.getGuidanceNote(),
                saved.getDueDate(),
                saved.getStatus(),
                saved.getRequestedBy(),
                saved.getRequestedAt(),
                saved.getRespondedAt(),
                stored.stream().map(RequestedChangeItem::from).toList(),
                saved.getVersion(),
                brief.getStage()
        );
    }

    /** The submitter's email: written for them, so it carries the guidance note and the due date only. */
    private void notifySubmitter(Submission brief, RefinementRequest request, String actor, Instant now) {
        String recipient = emailOrNull(brief.getSubmitterEmail());
        if (recipient == null) {
            recipient = emailOrNull(brief.getSubmitterId());
        }
        if (recipient == null) {
            return;
        }
        outbox.recordSubmitterNotification(
                brief.getId(),
                REFINEMENT_EVENT,
                brief.getCode(),
                recipient,
                "Changes requested for submission " + brief.getCode(),
                "Your brief needs some changes before it can continue. Please send your improved version by "
                        + request.getDueDate() + ".\n\nGuidance: " + request.getGuidanceNote(),
                actor,
                now
        );
    }

    private static String guidanceNote(String value) {
        String note = value == null ? "" : value.trim();
        if (note.isEmpty()) {
            throw unprocessable("guidanceNote is required and must not be blank.");
        }
        if (note.length() > MAX_GUIDANCE_LENGTH) {
            throw unprocessable("guidanceNote must be at most %d characters.".formatted(MAX_GUIDANCE_LENGTH));
        }
        return note;
    }

    private LocalDate dueDate(LocalDate value) {
        if (value == null) {
            throw unprocessable("dueDate is required.");
        }
        if (value.isBefore(LocalDate.now(clock.withZone(HUB_ZONE)))) {
            throw unprocessable("dueDate must not be in the past.");
        }
        return value;
    }

    private static List<String> requestedChanges(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw unprocessable("requestedChanges must contain at least one item.");
        }
        if (values.size() > MAX_CHANGES) {
            throw unprocessable("requestedChanges must contain at most %d items.".formatted(MAX_CHANGES));
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            String text = value == null ? "" : value.trim();
            if (text.isEmpty()) {
                throw unprocessable("requestedChanges must not contain a blank item.");
            }
            if (text.length() > MAX_CHANGE_LENGTH) {
                throw unprocessable("requestedChanges items must be at most %d characters."
                        .formatted(MAX_CHANGE_LENGTH));
            }
            cleaned.add(text);
        }
        return cleaned;
    }

    static <T> T persist(Supplier<T> write) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SubmissionConflictException(CHANGED);
        } catch (DataIntegrityViolationException exception) {
            if (!mentions(exception, CYCLE_CONSTRAINT)) {
                throw exception;
            }
            throw new SubmissionConflictException(CHANGED);
        }
    }

    private static boolean mentions(Throwable exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private static String emailOrNull(String value) {
        return value != null && value.contains("@") && !value.isBlank() ? value.trim() : null;
    }

    private static Caller requireCaller(Authentication authentication) {
        boolean officer = has(authentication, "ROLE_FACT_FINDING_OFFICER");
        boolean operator = has(authentication, "ROLE_INNOVATION_COORDINATOR") || has(authentication, "ROLE_ADMINISTRATOR");
        if (!officer && !operator) {
            throw new AccessDeniedException("Only fact-finding officers, coordinators and administrators can ask "
                    + "for changes.");
        }
        return new Caller(officer && !operator, operator);
    }

    static boolean has(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private static ResponseStatusException unprocessable(String detail) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    private record Caller(boolean officer, boolean operator) {
    }
}
