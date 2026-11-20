package org.unipod.unipoderpbackend.service.factfinding;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.factfinding.FactFindingResponse;
import org.unipod.unipoderpbackend.dto.factfinding.NeedsMoreInformationRequest;
import org.unipod.unipoderpbackend.dto.submission.RefinementRequestAskRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.submission.CaseFileService;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

/**
 * The fact-finding officer's "needs more information" outcome: completes the brief's fact-finding check with result
 * NEEDS_MORE_INFORMATION and asks the submitter for changes through Waluube's {@link CaseFileService#askRefinement},
 * which makes the stage change (IN_FACT_FINDING to IMPROVEMENT_REQUESTED), the refinement request, the history row,
 * the notification and its own audit entry. Everything runs in one transaction: if any part fails, the check stays a
 * draft and the stage is unchanged.
 */
@Service
public class FactFindingNeedsInformationService {
    static final String OFFICER_ONLY = "hasRole('FACT_FINDING_OFFICER')";
    static final int MAX_GUIDANCE_LENGTH = 2000;
    static final int MAX_CHANGES = 50;
    static final int MAX_CHANGE_LENGTH = 500;
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    static final String FORWARDED_NOT_STARTED = "Start the first check before asking for more information.";
    static final String STAGE_REQUIRED = "More information can only be asked for while the case is in fact-finding.";
    static final String NO_DRAFT = "Save the fact-finding check before asking for more information.";
    static final String ALREADY_COMPLETED = "This fact-finding check is already completed.";
    static final String CHANGED_BY_ANOTHER_USER =
            "This fact-finding check was changed by another user. Reload it before saving.";
    private static final String BRIEF_ROUND_CONSTRAINT = "uq_fact_finding_checks_brief_round";

    private final SubmissionRepository submissions;
    private final FactFindingRepository checks;
    private final CaseFileService caseFiles;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public FactFindingNeedsInformationService(
            SubmissionRepository submissions,
            FactFindingRepository checks,
            CaseFileService caseFiles,
            AuditTrail auditTrail,
            Clock clock
    ) {
        this.submissions = submissions;
        this.checks = checks;
        this.caseFiles = caseFiles;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize(OFFICER_ONLY)
    public FactFindingResponse needsMoreInformation(
            UUID briefId,
            NeedsMoreInformationRequest request,
            Authentication authentication
    ) {
        boolean officer = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_FACT_FINDING_OFFICER".equals(authority.getAuthority()));
        if (!officer) {
            throw new AccessDeniedException("Only the fact-finding officer can ask for more information.");
        }
        Submission brief = submissions.findVisibleByIdForUpdate(
                        briefId, SubmissionVisibility.scope(authentication), authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
        if (brief.getStage() == SubmissionStage.FORWARDED) {
            throw new SubmissionConflictException(FORWARDED_NOT_STARTED);
        }
        if (brief.getStage() != SubmissionStage.IN_FACT_FINDING) {
            throw new SubmissionConflictException(STAGE_REQUIRED);
        }
        FactFindingCheck check = checks.findTopByBriefIdOrderByRoundDesc(briefId)
                .orElseThrow(() -> new SubmissionConflictException(NO_DRAFT));
        if (check.getCompletedAt() != null) {
            throw new SubmissionConflictException(ALREADY_COMPLETED);
        }
        if (request.version() == null) {
            throw unprocessable("version is required when asking for more information.");
        }
        if (!Objects.equals(check.getVersion(), request.version())) {
            throw new SubmissionConflictException(CHANGED_BY_ANOTHER_USER);
        }
        if (request.caseVersion() == null) {
            throw unprocessable("caseVersion is required to move the brief to improvement requested.");
        }
        if (!hasMissingItemOrNote(check)) {
            throw unprocessable("missingItems: Add at least one missing item or note.");
        }
        String guidance = guidanceNote(request.guidanceNote());
        LocalDate dueDate = dueDate(request.dueDate());
        List<String> changes = requestedChanges(request.requestedChanges());

        Instant now = clock.instant();
        check.complete(FactFindingResult.NEEDS_MORE_INFORMATION, now);
        persist(check);
        // Waluube's ask: version check, roles, stage change, refinement request (source FACT_FINDING for an officer),
        // history row, notification and audit; joins this transaction and its exceptions propagate unchanged.
        caseFiles.askRefinement(briefId,
                new RefinementRequestAskRequest(guidance, dueDate, changes, request.caseVersion()), authentication);
        // Submitters can read this brief's activity: never put notes, missing items or guidance here.
        auditTrail.record(authentication.getName(), "submission.fact_finding_completed", briefId, null,
                AuditTrail.details(
                        "round", check.getRound(),
                        "result", check.getResult().name(),
                        "version", check.getVersion()
                ));
        submissions.flush();
        return FactFindingResponse.from(brief, check);
    }

    private static boolean hasMissingItemOrNote(FactFindingCheck check) {
        return Stream.of(check.getMissingItems(), check.getTitleNote(), check.getPurposeNote(),
                        check.getEvidenceNote(), check.getRelevantNote())
                .anyMatch(value -> value != null && !value.isBlank());
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

    /** The same conversion as FactFindingCommandService.persist. */
    private FactFindingCheck persist(FactFindingCheck check) {
        try {
            return checks.saveAndFlush(check);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SubmissionConflictException(CHANGED_BY_ANOTHER_USER);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause.getMessage() != null && cause.getMessage().contains(BRIEF_ROUND_CONSTRAINT)) {
                    throw new SubmissionConflictException(CHANGED_BY_ANOTHER_USER);
                }
            }
            throw exception;
        }
    }

    private static ResponseStatusException unprocessable(String detail) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
