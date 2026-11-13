package org.unipod.unipoderpbackend.service.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unipod.unipoderpbackend.dto.review.AssignReviewerRequest;
import org.unipod.unipoderpbackend.dto.review.SaveReviewSettingsRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/** Constraint and optimistic-lock conversion, as in FactFindingCommandServiceTest. */
class ReviewCommandServiceTest {
    private static final String COORDINATOR = "coord@example.test";
    private static final String REVIEWER = "reviewer@example.test";

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final ReviewSettingsRepository settings = mock(ReviewSettingsRepository.class);
    private final ReviewAssignmentRepository assignments = mock(ReviewAssignmentRepository.class);
    private final ApiUserRepository users = mock(ApiUserRepository.class);
    private final ReviewPanelQueryService panels = mock(ReviewPanelQueryService.class);
    private final ReviewCommandService service = new ReviewCommandService(
            submissions, settings, assignments, users, panels,
            Clock.fixed(Instant.parse("2026-11-12T08:00:00Z"), ZoneId.of("Africa/Kampala")));

    private final Submission brief = underReviewBrief();

    @Test
    void settingsBriefRoundUniqueViolationBecomesConflict() {
        lockable();
        when(settings.findTopByBriefIdOrderByRoundDesc(brief.getId())).thenReturn(Optional.empty());
        when(settings.saveAndFlush(any(ReviewSettings.class)))
                .thenThrow(constraintViolation("uq_review_settings_brief_round"));

        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> service.saveSettings(brief.getId(), newSettings(), coordinator()));

        assertEquals(ReviewCommandService.SETTINGS_CHANGED, thrown.getMessage());
    }

    @Test
    void settingsOptimisticLockFailureBecomesConflict() {
        lockable();
        when(settings.findTopByBriefIdOrderByRoundDesc(brief.getId())).thenReturn(Optional.empty());
        when(settings.saveAndFlush(any(ReviewSettings.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(ReviewSettings.class, 3L));

        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> service.saveSettings(brief.getId(), newSettings(), coordinator()));

        assertEquals(ReviewCommandService.SETTINGS_CHANGED, thrown.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "uq_review_assignments_brief_round_reviewer, This reviewer is already assigned to this brief.",
            "uq_review_assignments_brief_round_position, The reviewers for this brief were changed by another user. Reload them and try again."
    })
    void assignmentUniqueViolationsBecomeConflicts(String constraint, String expectedMessage) {
        assignable();
        when(assignments.saveAndFlush(any(ReviewAssignment.class))).thenThrow(constraintViolation(constraint));

        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> service.assignReviewer(brief.getId(), new AssignReviewerRequest(REVIEWER, null), coordinator()));

        assertEquals(expectedMessage, thrown.getMessage());
    }

    @Test
    void assignmentOptimisticLockFailureBecomesConflict() {
        assignable();
        when(assignments.saveAndFlush(any(ReviewAssignment.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(ReviewAssignment.class, 9L));

        SubmissionConflictException thrown = assertThrows(SubmissionConflictException.class,
                () -> service.assignReviewer(brief.getId(), new AssignReviewerRequest(REVIEWER, null), coordinator()));

        assertEquals(ReviewCommandService.ASSIGNMENTS_CHANGED, thrown.getMessage());
    }

    @Test
    void otherIntegrityViolationsAreRethrownUnchanged() {
        assignable();
        DataIntegrityViolationException other = constraintViolation("ck_review_assignments_position");
        when(assignments.saveAndFlush(any(ReviewAssignment.class))).thenThrow(other);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.assignReviewer(brief.getId(), new AssignReviewerRequest(REVIEWER, null), coordinator()));

        assertSame(other, thrown);
    }

    @Test
    void otherSettingsIntegrityViolationsAreRethrownUnchanged() {
        lockable();
        when(settings.findTopByBriefIdOrderByRoundDesc(brief.getId())).thenReturn(Optional.empty());
        DataIntegrityViolationException other = constraintViolation("ck_review_settings_reviews_needed");
        when(settings.saveAndFlush(any(ReviewSettings.class))).thenThrow(other);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.saveSettings(brief.getId(), newSettings(), coordinator()));

        assertSame(other, thrown);
    }

    private void lockable() {
        when(submissions.findVisibleByIdForUpdate(brief.getId(), "ALL", COORDINATOR)).thenReturn(Optional.of(brief));
    }

    private void assignable() {
        lockable();
        when(settings.findTopByBriefIdOrderByRoundDesc(brief.getId())).thenReturn(Optional.of(
                new ReviewSettings(brief.getId(), 1, ReviewMode.PARALLEL, 2, null, COORDINATOR)));
        when(users.findByEmailIgnoreCase(REVIEWER)).thenReturn(Optional.of(new ApiUser(
                UUID.randomUUID(), REVIEWER, "hash", ApiUserRole.REVIEWER, Instant.now())));
        when(assignments.findByBriefIdAndRoundOrderByPositionAsc(brief.getId(), 1)).thenReturn(List.of());
    }

    private static SaveReviewSettingsRequest newSettings() {
        return new SaveReviewSettingsRequest(ReviewMode.PARALLEL, 2, null, null);
    }

    private static DataIntegrityViolationException constraintViolation(String constraint) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    private static Authentication coordinator() {
        return new UsernamePasswordAuthenticationToken(
                COORDINATOR, "n/a", List.of(new SimpleGrantedAuthority("ROLE_INNOVATION_COORDINATOR")));
    }

    private static Submission underReviewBrief() {
        Submission brief = new Submission(
                UUID.randomUUID(), SubmissionCategory.GENERAL, "Brief", "Purpose", "Problem",
                "receptionist@example.test", "submitter@example.test", null, null, null, null,
                Instant.parse("2026-11-01T08:00:00Z"));
        brief.register("CASE-UT-1", Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        brief.transitionTo(SubmissionStage.REGISTERED, time);
        brief.transitionTo(SubmissionStage.FACT_FINDING, time);
        brief.transitionTo(SubmissionStage.UNDER_REVIEW, time);
        return brief;
    }
}
