package org.unipod.unipoderpbackend.service.improvement;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.improvement.ImprovedVersionResponse;
import org.unipod.unipoderpbackend.dto.improvement.MyImprovementResponse;
import org.unipod.unipoderpbackend.dto.improvement.RequestedChangeItem;
import org.unipod.unipoderpbackend.dto.improvement.SubmitImprovedVersionRequest;
import org.unipod.unipoderpbackend.dto.submission.ReplySubmissionRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionService;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

/**
 * The submitter's side of an improvement request: reading what is asked of them, and answering with an
 * improved version. The stage change, brief edits, history row and notifications are made by Waluube's
 * {@link SubmissionService#reply}, which joins this transaction: if the revision, the request update or
 * the audit entry fail, all of it rolls back.
 */
@Service
public class ImprovementResponseService {
    static final String SUBMITTER_ONLY = "hasRole('SUBMITTER')";
    static final int MAX_WHAT_CHANGED_LENGTH = 2000;
    static final String NOTHING_WAITING = "There is no improvement request waiting for your answer.";
    static final String REQUEST_CHANGED = "This request was changed. Reload it and try again.";
    static final String ALREADY_ANSWERED = "This improvement request was already answered.";
    private static final List<String> ANSWER_CONSTRAINTS =
            List.of("uq_case_revisions_brief_version", "uq_case_revisions_request");

    private final SubmissionRepository submissions;
    private final RefinementRequestRepository requests;
    private final RefinementRequestedChangeRepository changes;
    private final CaseRevisionRepository revisions;
    private final SubmissionService submissionService;
    private final ImprovementAudit audit;
    private final Validator validator;
    private final Clock clock;

    public ImprovementResponseService(
            SubmissionRepository submissions,
            RefinementRequestRepository requests,
            RefinementRequestedChangeRepository changes,
            CaseRevisionRepository revisions,
            SubmissionService submissionService,
            ImprovementAudit audit,
            Validator validator,
            Clock clock
    ) {
        this.submissions = submissions;
        this.requests = requests;
        this.changes = changes;
        this.revisions = revisions;
        this.submissionService = submissionService;
        this.audit = audit;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(SUBMITTER_ONLY)
    public MyImprovementResponse myImprovement(UUID briefId, Authentication authentication) {
        requireSubmitter(authentication);
        Submission brief = submissions.findVisibleById(
                        briefId, SubmissionVisibility.scope(authentication), authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));

        List<RefinementRequest> asked = requests.findByBriefIdOrderByCycleAsc(briefId);
        if (asked.isEmpty()) {
            return new MyImprovementResponse(briefId, brief.getStage(), null, List.of());
        }
        Map<Long, List<RequestedChangeItem>> changesByRequest = changes
                .findByRequestIdInOrderByRequestIdAscPositionAsc(asked.stream().map(RefinementRequest::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(change -> change.getRequestId(),
                        Collectors.mapping(RequestedChangeItem::from, Collectors.toList())));
        Map<Long, CaseRevision> revisionByRequest = revisions.findByBriefIdOrderByVersionNumberAsc(briefId).stream()
                .collect(Collectors.toMap(CaseRevision::getRequestId, Function.identity()));

        MyImprovementResponse.OpenRequest open = null;
        List<MyImprovementResponse.PreviousRequest> previous = new ArrayList<>();
        for (RefinementRequest request : asked) {
            List<RequestedChangeItem> items = changesByRequest.getOrDefault(request.getId(), List.of());
            if (request.getStatus() == RefinementStatus.OPEN) {
                open = new MyImprovementResponse.OpenRequest(request.getId(), request.getCycle(),
                        request.getGuidanceNote(), request.getDueDate(), items, request.getVersion());
            } else {
                CaseRevision revision = revisionByRequest.get(request.getId());
                previous.add(new MyImprovementResponse.PreviousRequest(request.getCycle(), request.getGuidanceNote(),
                        request.getDueDate(), items, request.getRespondedAt(),
                        revision == null ? null : new MyImprovementResponse.Revision(
                                revision.getVersionNumber(), revision.getWhatChanged(), revision.getSubmittedAt(),
                                revision.getId())));
            }
        }
        return new MyImprovementResponse(briefId, brief.getStage(), open, previous);
    }

    @Transactional
    @PreAuthorize(SUBMITTER_ONLY)
    public ImprovedVersionResponse submit(UUID briefId, SubmitImprovedVersionRequest request, Authentication authentication) {
        requireSubmitter(authentication);
        // The submitter's own-case lookup with the row lock: a non-owner gets the same 404 as an unknown brief.
        Submission brief = submissions.findVisibleByIdForUpdate(
                        briefId, SubmissionVisibility.scope(authentication), authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));

        if (brief.getStage() != SubmissionStage.IMPROVEMENT_REQUESTED) {
            throw new SubmissionConflictException(NOTHING_WAITING);
        }
        RefinementRequest open = requests.findTopByBriefIdOrderByCycleDesc(briefId)
                .filter(latest -> latest.getStatus() == RefinementStatus.OPEN)
                .orElseThrow(() -> new SubmissionConflictException(NOTHING_WAITING));
        if (request.requestVersion() == null) {
            throw unprocessable("requestVersion is required.");
        }
        if (!Objects.equals(open.getVersion(), request.requestVersion())) {
            throw new SubmissionConflictException(REQUEST_CHANGED);
        }
        String whatChanged = request.whatChanged() == null ? "" : request.whatChanged().trim();
        if (whatChanged.isEmpty()) {
            throw unprocessable("whatChanged is required and must not be blank.");
        }
        if (whatChanged.length() > MAX_WHAT_CHANGED_LENGTH) {
            throw unprocessable("whatChanged must be at most %d characters.".formatted(MAX_WHAT_CHANGED_LENGTH));
        }

        ReplySubmissionRequest reply = new ReplySubmissionRequest(
                request.title(),
                request.purpose(),
                request.problemAddressed(),
                request.studentNumber(),
                request.programme(),
                request.supervisor(),
                request.recommendationLetterStatus(),
                request.submitterPhone(),
                request.serviceRequested(),
                request.department(),
                request.expectedResult(),
                request.preferredAt(),
                whatChanged
        );
        // The same field limits Waluube's reply endpoint enforces with @Valid, reported the same way (422).
        Set<ConstraintViolation<ReplySubmissionRequest>> violations = validator.validate(reply);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        // Waluube's reply: brief edits, brief_version, stage RESUBMITTED, history, audit, notifications.
        submissionService.reply(briefId, reply, authentication.getName());

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int versionNumber = revisions.findTopByBriefIdOrderByVersionNumberDesc(briefId)
                .map(CaseRevision::getVersionNumber)
                .orElse(1) + 1;
        CaseRevision revision = persist(() -> revisions.saveAndFlush(new CaseRevision(
                briefId, versionNumber, open.getId(), whatChanged, authentication.getName(), now)));
        open.setStatus(RefinementStatus.RESPONDED);
        open.setRespondedAt(now);
        persist(() -> requests.saveAndFlush(open));

        // Structural values only: no whatChanged text and no field values (see ImprovementAudit).
        audit.improvedVersionSubmitted(authentication.getName(), open, versionNumber);
        return new ImprovedVersionResponse(briefId, revision.getVersionNumber(), revision.getWhatChanged(),
                revision.getSubmittedAt(), open.getCycle(), brief.getStage(), revision.getId());
    }

    static <T> T persist(Supplier<T> write) {
        try {
            return write.get();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SubmissionConflictException(ALREADY_ANSWERED);
        } catch (DataIntegrityViolationException exception) {
            if (ANSWER_CONSTRAINTS.stream().noneMatch(constraint -> mentions(exception, constraint))) {
                throw exception;
            }
            throw new SubmissionConflictException(ALREADY_ANSWERED);
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

    private static void requireSubmitter(Authentication authentication) {
        if (!RefinementRequestService.has(authentication, "ROLE_SUBMITTER")) {
            throw new AccessDeniedException("Only the submitter can see or answer improvement requests here.");
        }
    }

    private static ResponseStatusException unprocessable(String detail) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
