package org.unipod.unipoderpbackend.service.factfinding;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.factfinding.CompleteFactFindingRequest;
import org.unipod.unipoderpbackend.dto.factfinding.FactFindingResponse;
import org.unipod.unipoderpbackend.dto.factfinding.SaveFactFindingRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.submission.CaseStageHistory;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

@Service
public class FactFindingCommandService {
    private static final String FACT_FINDING_WRITE_ROLES = "hasRole('FACT_FINDING_OFFICER')";
    private static final String CHANGED_BY_ANOTHER_USER =
            "This fact-finding check was changed by another user. Reload it before saving.";
    private static final String BRIEF_ROUND_CONSTRAINT = "uq_fact_finding_checks_brief_round";
    private static final int MAX_NOTE_LENGTH = 1000;
    private static final int MAX_MISSING_ITEMS_LENGTH = 2000;

    private final SubmissionRepository submissions;
    private final FactFindingRepository checks;
    private final CaseStageHistoryRepository history;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public FactFindingCommandService(
            SubmissionRepository submissions,
            FactFindingRepository checks,
            CaseStageHistoryRepository history,
            AuditTrail auditTrail,
            Clock clock
    ) {
        this.submissions = submissions;
        this.checks = checks;
        this.history = history;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize(FACT_FINDING_WRITE_ROLES)
    public FactFindingResponse saveDraft(
            UUID briefId,
            SaveFactFindingRequest request,
            Authentication authentication
    ) {
        requireLengthLimits(request);
        Submission brief = visibleCaseForUpdate(briefId, authentication);
        requireSavableStage(brief.getStage());
        FactFindingCheck check = saveAnswers(briefId, request, authentication.getName());
        // Submitters can read this brief's activity: never put notes, missing items or answers here.
        auditTrail.record(authentication.getName(), "submission.fact_finding_saved", briefId, null,
                AuditTrail.details(
                        "state", "DRAFT",
                        "round", check.getRound(),
                        "version", check.getVersion()
                ));
        return FactFindingResponse.from(brief, check);
    }

    @Transactional
    @PreAuthorize(FACT_FINDING_WRITE_ROLES)
    public FactFindingResponse complete(
            UUID briefId,
            CompleteFactFindingRequest request,
            Authentication authentication
    ) {
        Submission brief = visibleCaseForUpdate(briefId, authentication);
        requireCompletableStage(brief.getStage());
        FactFindingCheck check = checks.findTopByBriefIdOrderByRoundDesc(briefId)
                .orElseThrow(() -> new SubmissionConflictException(
                        "Save the fact-finding check before completing it."
                ));
        if (check.getCompletedAt() != null) {
            throw new SubmissionConflictException("This fact-finding check is already completed.");
        }
        if (request.version() == null) {
            throw unprocessable("version is required when completing a fact-finding check.");
        }
        if (!Objects.equals(check.getVersion(), request.version())) {
            throw changedByAnotherUser();
        }
        requireAllQuestionsAnswered(check);

        Instant now = clock.instant();
        SubmissionStage previousStage = brief.getStage();
        check.complete(FactFindingResult.READY_FOR_REVIEW, now);
        persist(check);
        brief.transitionTo(SubmissionStage.READY_FOR_REVIEW, now);
        history.save(new CaseStageHistory(
                UUID.randomUUID(),
                briefId,
                previousStage,
                SubmissionStage.READY_FOR_REVIEW,
                authentication.getName(),
                "Fact-finding complete: ready for review.",
                now
        ));
        // Submitters can read this brief's activity: never put notes, missing items or answers here.
        auditTrail.record(authentication.getName(), "submission.fact_finding_completed", briefId, null,
                AuditTrail.details(
                        "round", check.getRound(),
                        "result", check.getResult().name(),
                        "version", check.getVersion()
                ));
        submissions.flush();
        return FactFindingResponse.from(brief, check);
    }

    private FactFindingCheck saveAnswers(UUID briefId, SaveFactFindingRequest request, String officer) {
        FactFindingCheck check = checks.findTopByBriefIdOrderByRoundDesc(briefId)
                .map(existing -> requireDraftToReplace(existing, request.version()))
                .orElseGet(() -> newRoundOne(briefId, request.version(), officer));
        applyAnswers(check, request);
        return persist(check);
    }

    private FactFindingCheck persist(FactFindingCheck check) {
        try {
            return checks.saveAndFlush(check);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw changedByAnotherUser();
        } catch (DataIntegrityViolationException exception) {
            if (!causedByBriefRoundConstraint(exception)) {
                throw exception;
            }
            throw changedByAnotherUser();
        }
    }

    private static boolean causedByBriefRoundConstraint(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(BRIEF_ROUND_CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }

    private static SubmissionConflictException changedByAnotherUser() {
        return new SubmissionConflictException(CHANGED_BY_ANOTHER_USER);
    }

    private static FactFindingCheck newRoundOne(UUID briefId, Long version, String officer) {
        if (version != null) {
            throw unprocessable("version must not be supplied when starting a new fact-finding check.");
        }
        return new FactFindingCheck(briefId, 1, officer);
    }

    private static FactFindingCheck requireDraftToReplace(FactFindingCheck check, Long version) {
        if (check.getCompletedAt() != null) {
            throw new SubmissionConflictException("A completed fact-finding check cannot be edited.");
        }
        if (version == null) {
            throw unprocessable("version is required when updating an existing fact-finding check.");
        }
        if (!Objects.equals(check.getVersion(), version)) {
            throw changedByAnotherUser();
        }
        return check;
    }

    private static void applyAnswers(FactFindingCheck check, SaveFactFindingRequest request) {
        check.setTitleClear(request.titleClear());
        check.setTitleNote(request.titleNote());
        check.setPurposeClear(request.purposeClear());
        check.setPurposeNote(request.purposeNote());
        check.setEvidenceEnough(request.evidenceEnough());
        check.setEvidenceNote(request.evidenceNote());
        check.setRelevantToHub(request.relevantToHub());
        check.setRelevantNote(request.relevantNote());
        check.setMissingItems(request.missingItems());
    }

    private static void requireAllQuestionsAnswered(FactFindingCheck check) {
        List<String> missing = new ArrayList<>();
        if (check.getTitleClear() == null) {
            missing.add("titleClear");
        }
        if (check.getPurposeClear() == null) {
            missing.add("purposeClear");
        }
        if (check.getEvidenceEnough() == null) {
            missing.add("evidenceEnough");
        }
        if (check.getRelevantToHub() == null) {
            missing.add("relevantToHub");
        }
        if (!missing.isEmpty()) {
            throw unprocessable(
                    "Answer all four questions before completing: %s.".formatted(String.join(", ", missing))
            );
        }
    }

    private Submission visibleCaseForUpdate(UUID briefId, Authentication authentication) {
        String scope = SubmissionVisibility.scope(authentication);
        return submissions.findVisibleByIdForUpdate(briefId, scope, authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
    }

    private static void requireSavableStage(SubmissionStage stage) {
        if (stage != SubmissionStage.FORWARDED && stage != SubmissionStage.IN_FACT_FINDING) {
            throw new SubmissionConflictException(
                    "Fact-finding answers can only be saved while the case is forwarded or in fact-finding."
            );
        }
    }

    private static void requireCompletableStage(SubmissionStage stage) {
        if (stage != SubmissionStage.FORWARDED && stage != SubmissionStage.IN_FACT_FINDING) {
            throw new SubmissionConflictException(
                    "Fact-finding can only be completed while the case is forwarded or in fact-finding."
            );
        }
    }

    private static void requireLengthLimits(SaveFactFindingRequest request) {
        requireMaxLength("titleNote", request.titleNote(), MAX_NOTE_LENGTH);
        requireMaxLength("purposeNote", request.purposeNote(), MAX_NOTE_LENGTH);
        requireMaxLength("evidenceNote", request.evidenceNote(), MAX_NOTE_LENGTH);
        requireMaxLength("relevantNote", request.relevantNote(), MAX_NOTE_LENGTH);
        requireMaxLength("missingItems", request.missingItems(), MAX_MISSING_ITEMS_LENGTH);
    }

    private static void requireMaxLength(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw unprocessable("%s must be at most %d characters.".formatted(field, maxLength));
        }
    }

    private static ResponseStatusException unprocessable(String detail) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
