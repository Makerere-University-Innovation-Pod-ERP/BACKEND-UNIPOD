package org.unipod.unipoderpbackend.service.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;

/** The contract other modules rely on: quorum of the latest review round, read-only. */
class ReviewQuorumServiceTest {
    private static final UUID BRIEF = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final ReviewSettingsRepository settings = mock(ReviewSettingsRepository.class);
    private final ReviewAssignmentRepository assignments = mock(ReviewAssignmentRepository.class);
    private final ReviewQuorumService service = new ReviewQuorumService(settings, assignments);

    @Test
    void noSettingsMeansNotConfigured() {
        when(settings.findTopByBriefIdOrderByRoundDesc(BRIEF)).thenReturn(Optional.empty());

        assertEquals(new ReviewQuorum(false, null, 0, 0, false), service.quorum(BRIEF));
        verifyNoInteractions(assignments);
    }

    @Test
    void twoNeededNoneSubmittedIsNotMet() {
        latest(1, 2);
        submitted(1, 0);

        assertEquals(new ReviewQuorum(true, 1, 2, 0, false), service.quorum(BRIEF));
    }

    @Test
    void oneOfTwoSubmittedIsNotMet() {
        latest(1, 2);
        submitted(1, 1);

        assertEquals(new ReviewQuorum(true, 1, 2, 1, false), service.quorum(BRIEF));
    }

    @Test
    void twoOfTwoSubmittedIsMet() {
        latest(1, 2);
        submitted(1, 2);

        assertEquals(new ReviewQuorum(true, 1, 2, 2, true), service.quorum(BRIEF));
    }

    @Test
    void moreSubmittedThanNeededIsMet() {
        latest(1, 2);
        submitted(1, 3);

        assertEquals(new ReviewQuorum(true, 1, 2, 3, true), service.quorum(BRIEF));
    }

    @Test
    void newRoundWithNothingSubmittedIsNotMetEvenIfPreviousRoundWasComplete() {
        latest(2, 2);
        submitted(1, 2);
        submitted(2, 0);

        assertEquals(new ReviewQuorum(true, 2, 2, 0, false), service.quorum(BRIEF));
        verify(assignments, never()).countByBriefIdAndRoundAndStatus(eq(BRIEF), eq(1), eq(ReviewAssignmentStatus.SUBMITTED));
    }

    @Test
    void countsOnlyTheLatestRoundsSubmissions() {
        latest(2, 2);
        submitted(1, 3);
        submitted(2, 1);

        assertEquals(new ReviewQuorum(true, 2, 2, 1, false), service.quorum(BRIEF));
        verify(assignments).countByBriefIdAndRoundAndStatus(BRIEF, 2, ReviewAssignmentStatus.SUBMITTED);
        verify(assignments, never()).countByBriefIdAndRoundAndStatus(eq(BRIEF), eq(1), eq(ReviewAssignmentStatus.SUBMITTED));
        verify(assignments, never()).countByBriefIdAndRoundAndStatus(eq(BRIEF), anyInt(), eq(ReviewAssignmentStatus.NOT_STARTED));
    }

    private void latest(int round, int reviewsNeeded) {
        when(settings.findTopByBriefIdOrderByRoundDesc(BRIEF)).thenReturn(Optional.of(new ReviewSettings(
                BRIEF, round, ReviewMode.PARALLEL, reviewsNeeded, null, "coord@example.test")));
    }

    private void submitted(int round, long count) {
        when(assignments.countByBriefIdAndRoundAndStatus(BRIEF, round, ReviewAssignmentStatus.SUBMITTED))
                .thenReturn(count);
    }
}
