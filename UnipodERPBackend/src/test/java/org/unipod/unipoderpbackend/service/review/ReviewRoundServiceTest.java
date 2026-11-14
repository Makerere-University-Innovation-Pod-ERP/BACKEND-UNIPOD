package org.unipod.unipoderpbackend.service.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.unipod.unipoderpbackend.dto.review.StartReviewRoundRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

class ReviewRoundServiceTest {

    @Test
    void optimisticLockFailureBecomesAConflict() {
        SubmissionConflictException conflict = assertThrows(SubmissionConflictException.class,
                () -> ReviewRoundService.persist(() -> {
                    throw new ObjectOptimisticLockingFailureException(ReviewSettings.class, 1L);
                }));
        assertEquals(ReviewRoundService.SETTINGS_CHANGED, conflict.getMessage());
    }

    @Test
    void duplicateRoundBecomesAConflict() {
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException("could not execute",
                new ConstraintViolationException("duplicate key", new SQLException(
                        "ERROR: duplicate key value violates unique constraint \"uq_review_settings_brief_round\""),
                        "uq_review_settings_brief_round"));
        SubmissionConflictException conflict = assertThrows(SubmissionConflictException.class,
                () -> ReviewRoundService.persist(() -> {
                    throw duplicate;
                }));
        assertEquals(ReviewRoundService.SETTINGS_CHANGED, conflict.getMessage());
    }

    @Test
    void otherConstraintViolationsAreRethrownUnchanged() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("could not execute",
                new SQLException("ERROR: null value in column \"mode\" violates not-null constraint"));
        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> ReviewRoundService.persist(() -> {
                    throw other;
                }));
        assertSame(other, thrown);
    }

    @Test
    void successfulWriteIsReturned() {
        assertEquals("saved", ReviewRoundService.persist(() -> "saved"));
    }

    @Test
    void serviceChecksTheRoleItselfBeforeReadingAnything() {
        SubmissionRepository submissions = mock(SubmissionRepository.class);
        ReviewSettingsRepository settings = mock(ReviewSettingsRepository.class);
        ReviewRoundService service = new ReviewRoundService(submissions, settings, mock(CaseRevisionRepository.class),
                mock(ReviewQuorumService.class), mock(ReviewPanelQueryService.class), mock(ReviewAudit.class),
                Clock.systemUTC());
        StartReviewRoundRequest request = new StartReviewRoundRequest(ReviewMode.PARALLEL, 1, null, 0L);

        for (String role : List.of("ROLE_REVIEWER", "ROLE_SUBMITTER", "ROLE_FACT_FINDING_OFFICER")) {
            assertThrows(AccessDeniedException.class, () -> service.start(UUID.randomUUID(), request,
                    new TestingAuthenticationToken("someone@example.test", null, role)));
        }
        assertThrows(AccessDeniedException.class, () -> service.start(UUID.randomUUID(), request, null));
        verifyNoInteractions(submissions, settings);
    }
}
