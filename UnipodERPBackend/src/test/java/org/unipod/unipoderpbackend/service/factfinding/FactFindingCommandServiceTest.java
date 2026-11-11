package org.unipod.unipoderpbackend.service.factfinding;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unipod.unipoderpbackend.dto.factfinding.SaveFactFindingRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FactFindingCommandServiceTest {
    private static final String OFFICER = "officer@example.test";
    private static final String CHANGED_BY_ANOTHER_USER =
            "This fact-finding check was changed by another user. Reload it before saving.";

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final FactFindingRepository checks = mock(FactFindingRepository.class);
    private final CaseStageHistoryRepository history = mock(CaseStageHistoryRepository.class);
    private final FactFindingCommandService service = new FactFindingCommandService(
            submissions,
            checks,
            history,
            Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void optimisticLockFailureBecomesConflict() {
        Submission brief = factFindingBrief();
        when(submissions.findVisibleById(any(), any(), any())).thenReturn(Optional.of(brief));
        when(checks.findTopByBriefIdOrderByRoundDesc(brief.getId())).thenReturn(Optional.empty());
        when(checks.saveAndFlush(any(FactFindingCheck.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(FactFindingCheck.class, 7L));

        SubmissionConflictException thrown = assertThrows(
                SubmissionConflictException.class,
                () -> service.saveDraft(brief.getId(), emptyAnswers(), authentication())
        );

        assertEquals(CHANGED_BY_ANOTHER_USER, thrown.getMessage());
    }

    @Test
    void briefRoundUniqueViolationBecomesConflict() {
        Submission brief = factFindingBrief();
        when(submissions.findVisibleById(any(), any(), any())).thenReturn(Optional.of(brief));
        when(checks.findTopByBriefIdOrderByRoundDesc(brief.getId())).thenReturn(Optional.empty());
        when(checks.saveAndFlush(any(FactFindingCheck.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement",
                        new SQLException(
                                "ERROR: duplicate key value violates unique constraint "
                                        + "\"uq_fact_finding_checks_brief_round\""
                        )
                ));

        SubmissionConflictException thrown = assertThrows(
                SubmissionConflictException.class,
                () -> service.saveDraft(brief.getId(), emptyAnswers(), authentication())
        );

        assertEquals(CHANGED_BY_ANOTHER_USER, thrown.getMessage());
    }

    @Test
    void unrelatedIntegrityViolationIsRethrownUnchanged() {
        Submission brief = factFindingBrief();
        when(submissions.findVisibleById(any(), any(), any())).thenReturn(Optional.of(brief));
        when(checks.findTopByBriefIdOrderByRoundDesc(brief.getId())).thenReturn(Optional.empty());
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "null value in column \"officer_id\" violates not-null constraint",
                new SQLException("null value in column \"officer_id\" violates not-null constraint")
        );
        when(checks.saveAndFlush(any(FactFindingCheck.class))).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.saveDraft(brief.getId(), emptyAnswers(), authentication())
        );

        assertSame(failure, thrown);
    }

    private static Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                OFFICER,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_FACT_FINDING_OFFICER"))
        );
    }

    private static SaveFactFindingRequest emptyAnswers() {
        return new SaveFactFindingRequest(
                null, null, null, null, null, null, null, null, null, null
        );
    }

    private static Submission factFindingBrief() {
        Submission brief = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Solar dryer",
                "Purpose",
                "Problem",
                "receptionist@example.test",
                "submitter@example.test",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-01T08:00:00Z")
        );
        brief.register("CASE-FF-U1", Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        brief.transitionTo(SubmissionStage.REGISTERED, Instant.parse("2026-11-01T10:00:00Z"));
        brief.transitionTo(SubmissionStage.FACT_FINDING, Instant.parse("2026-11-01T11:00:00Z"));
        return brief;
    }
}
