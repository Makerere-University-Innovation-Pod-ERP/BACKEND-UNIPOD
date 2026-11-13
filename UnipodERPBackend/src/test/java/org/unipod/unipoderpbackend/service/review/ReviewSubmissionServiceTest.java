package org.unipod.unipoderpbackend.service.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.unipod.unipoderpbackend.dto.review.SubmitReviewRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/** Persist conversion and the in-service role check, as in ReviewCommandServiceTest. */
class ReviewSubmissionServiceTest {
    private static final String REVIEWER = "reviewer@example.test";

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final ReviewSettingsRepository settings = mock(ReviewSettingsRepository.class);
    private final ReviewAssignmentRepository assignments = mock(ReviewAssignmentRepository.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ReviewQuorumService quorum = mock(ReviewQuorumService.class);
    private final ReviewSubmissionService service = new ReviewSubmissionService(
            submissions, settings, assignments, reviews, quorum,
            Clock.fixed(Instant.parse("2026-11-12T08:00:00Z"), ZoneId.of("Africa/Kampala")));

    private final Submission brief = underReviewBrief();

    @Test
    void reviewAssignmentUniqueViolationBecomesAlreadySubmittedConflict() {
        submittable();
        when(reviews.saveAndFlush(any(Review.class))).thenThrow(constraintViolation("uq_reviews_assignment"));

        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> service.submit(brief.getId(), request(), reviewer()));

        assertEquals(ReviewSubmissionService.ALREADY_SUBMITTED, thrown.getMessage());
    }

    @Test
    void assignmentOptimisticLockFailureBecomesAlreadySubmittedConflict() {
        submittable();
        when(reviews.saveAndFlush(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignments.saveAndFlush(any(ReviewAssignment.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(ReviewAssignment.class, 5L));

        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> service.submit(brief.getId(), request(), reviewer()));

        assertEquals(ReviewSubmissionService.ALREADY_SUBMITTED, thrown.getMessage());
    }

    @Test
    void reviewOptimisticLockFailureBecomesAlreadySubmittedConflict() {
        submittable();
        when(reviews.saveAndFlush(any(Review.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Review.class, 1L));

        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> service.submit(brief.getId(), request(), reviewer()));

        assertEquals(ReviewSubmissionService.ALREADY_SUBMITTED, thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fk_reviews_assignment", "ck_reviews_decision", "uq_review_assignments_brief_round_position"})
    void otherIntegrityViolationsAreRethrownUnchanged(String constraint) {
        submittable();
        DataIntegrityViolationException other = constraintViolation(constraint);
        when(reviews.saveAndFlush(any(Review.class))).thenThrow(other);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.submit(brief.getId(), request(), reviewer()));

        assertSame(other, thrown);
    }

    @Test
    void assignmentIntegrityViolationOtherThanTheReviewConstraintIsRethrownUnchanged() {
        submittable();
        when(reviews.saveAndFlush(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DataIntegrityViolationException other = constraintViolation("ck_review_assignments_status");
        when(assignments.saveAndFlush(any(ReviewAssignment.class))).thenThrow(other);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.submit(brief.getId(), request(), reviewer()));

        assertSame(other, thrown);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INNOVATION_COORDINATOR", "ADMINISTRATOR"})
    void serviceRejectsNonReviewersEvenWithoutMethodSecurity(String role) {
        Authentication other = new UsernamePasswordAuthenticationToken(
                REVIEWER, "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        assertThrows(AccessDeniedException.class, () -> service.submit(brief.getId(), request(), other));
        assertThrows(AccessDeniedException.class, () -> service.myReview(brief.getId(), other));
        verifyNoInteractions(submissions, settings, assignments, reviews, quorum);
    }

    private void submittable() {
        when(submissions.findByIdForUpdate(brief.getId())).thenReturn(Optional.of(brief));
        when(settings.findTopByBriefIdOrderByRoundDesc(brief.getId())).thenReturn(Optional.of(
                new ReviewSettings(brief.getId(), 1, ReviewMode.PARALLEL, 1, null, "coord@example.test")));
        ReviewAssignment assignment = new ReviewAssignment(brief.getId(), 1, REVIEWER, null, 1, "coord@example.test");
        ReflectionTestUtils.setField(assignment, "id", 7L);
        ReflectionTestUtils.setField(assignment, "version", 0L);
        when(assignments.findByBriefIdAndRoundOrderByPositionAsc(brief.getId(), 1)).thenReturn(List.of(assignment));
    }

    private static SubmitReviewRequest request() {
        return new SubmitReviewRequest(ReviewDecision.RECOMMENDED, "Fine.", 0L);
    }

    private static DataIntegrityViolationException constraintViolation(String constraint) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    private static Authentication reviewer() {
        return new UsernamePasswordAuthenticationToken(
                REVIEWER, "n/a", List.of(new SimpleGrantedAuthority("ROLE_REVIEWER")));
    }

    private static Submission underReviewBrief() {
        Submission brief = new Submission(
                UUID.randomUUID(), SubmissionCategory.GENERAL, "Brief", "Purpose", "Problem",
                "receptionist@example.test", "submitter@example.test", null, null, null, null,
                Instant.parse("2026-11-01T08:00:00Z"));
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-UT-2", Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        brief.transitionTo(SubmissionStage.FORWARDED, time);
        brief.transitionTo(SubmissionStage.IN_FACT_FINDING, time);
        brief.transitionTo(SubmissionStage.IN_REVIEW, time);
        return brief;
    }
}
