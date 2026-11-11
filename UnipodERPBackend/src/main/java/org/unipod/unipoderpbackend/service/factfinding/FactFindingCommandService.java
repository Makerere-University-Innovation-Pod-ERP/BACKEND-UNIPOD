package org.unipod.unipoderpbackend.service.factfinding;

import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.factfinding.FactFindingResponse;
import org.unipod.unipoderpbackend.dto.factfinding.SaveFactFindingRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionVisibility;

@Service
public class FactFindingCommandService {
    private static final String FACT_FINDING_WRITE_ROLES = "hasRole('FACT_FINDING_OFFICER')";
    private static final int MAX_NOTE_LENGTH = 1000;
    private static final int MAX_MISSING_ITEMS_LENGTH = 2000;

    private final SubmissionRepository submissions;
    private final FactFindingRepository checks;

    public FactFindingCommandService(SubmissionRepository submissions, FactFindingRepository checks) {
        this.submissions = submissions;
        this.checks = checks;
    }

    @Transactional
    @PreAuthorize(FACT_FINDING_WRITE_ROLES)
    public FactFindingResponse saveDraft(
            UUID briefId,
            SaveFactFindingRequest request,
            Authentication authentication
    ) {
        requireLengthLimits(request);
        Submission brief = visibleCase(briefId, authentication);
        requireSavableStage(brief.getStage());
        FactFindingCheck check = saveAnswers(briefId, request, authentication.getName());
        return FactFindingResponse.from(brief, check);
    }

    private FactFindingCheck saveAnswers(UUID briefId, SaveFactFindingRequest request, String officer) {
        FactFindingCheck check = checks.findTopByBriefIdOrderByRoundDesc(briefId)
                .map(existing -> requireDraftToReplace(existing, request.version()))
                .orElseGet(() -> newRoundOne(briefId, request.version(), officer));
        applyAnswers(check, request);
        return checks.saveAndFlush(check);
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
            throw new SubmissionConflictException(
                    "This fact-finding check was changed by another user. Reload it before saving."
            );
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

    private Submission visibleCase(UUID briefId, Authentication authentication) {
        String scope = SubmissionVisibility.scope(authentication);
        return submissions.findVisibleById(briefId, scope, authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(briefId));
    }

    private static void requireSavableStage(SubmissionStage stage) {
        if (stage != SubmissionStage.FORWARDED && stage != SubmissionStage.FACT_FINDING) {
            throw new SubmissionConflictException(
                    "Fact-finding answers can only be saved while the case is forwarded or in fact-finding."
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
