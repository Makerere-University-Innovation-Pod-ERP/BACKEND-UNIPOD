package org.unipod.unipoderpbackend.model.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.unipod.unipoderpbackend.exception.training.InvalidTrainingException;

class TrainingSessionTest {
    private static final Instant STARTS = Instant.parse("2026-12-01T06:00:00Z");
    private static final Instant ENDS = Instant.parse("2026-12-01T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @Test
    void isUpcomingBeforeItStarts() {
        assertEquals(TrainingStatus.UPCOMING, session(true).statusAt(STARTS.minusSeconds(1)));
    }

    @Test
    void isInProgressFromTheExactStartInstant() {
        assertEquals(TrainingStatus.IN_PROGRESS, session(true).statusAt(STARTS));
    }

    @Test
    void isStillInProgressJustBeforeItEnds() {
        assertEquals(TrainingStatus.IN_PROGRESS, session(true).statusAt(ENDS.minusSeconds(1)));
    }

    @Test
    void isCompletedAtTheExactEndInstant() {
        assertEquals(TrainingStatus.COMPLETED, session(true).statusAt(ENDS));
    }

    @Test
    void anUnpublishedSessionIsADraftWhateverTheTime() {
        TrainingSession draft = session(false);

        assertEquals(TrainingStatus.DRAFT, draft.statusAt(STARTS.minusSeconds(60)));
        assertEquals(TrainingStatus.DRAFT, draft.statusAt(STARTS.plusSeconds(60)));
        assertEquals(TrainingStatus.DRAFT, draft.statusAt(ENDS.plusSeconds(60)));
    }

    @Test
    void hasEndedFromTheExactEndInstantWhetherOrNotItIsPublished() {
        assertEquals(false, session(true).hasEndedAt(ENDS.minusSeconds(1)));
        assertEquals(true, session(true).hasEndedAt(ENDS));
        assertEquals(false, session(false).hasEndedAt(ENDS.minusSeconds(1)));
        assertEquals(true, session(false).hasEndedAt(ENDS));
    }

    @Test
    void endsTwoHoursAfterTheStartWhenNoEndIsGiven() {
        TrainingSession session = new TrainingSession(
                UUID.randomUUID(), UUID.randomUUID(), "Laser cutting", "Eng. Nsubuga", STARTS, null,
                20, "Lab 2", "Basics", 0, false, true, NOW);

        assertEquals(STARTS.plusSeconds(7200), session.getEndsAt());
    }

    @Test
    void keepsAnExplicitEndTime() {
        assertEquals(ENDS, session(true).getEndsAt());
    }

    @Test
    void trimsTextAndTurnsMissingDescriptionIntoEmptyText() {
        TrainingSession session = new TrainingSession(
                UUID.randomUUID(), UUID.randomUUID(), "  Laser cutting ", " Eng. Nsubuga ", STARTS, ENDS,
                20, " Lab 2 ", null, 0, false, true, NOW);

        assertEquals("Laser cutting", session.getTitle());
        assertEquals("Eng. Nsubuga", session.getFacilitator());
        assertEquals("Lab 2", session.getVenue());
        assertEquals("", session.getDescription());
    }

    @Test
    void recordsFeeSafetyFlagAndCreationTime() {
        TrainingSession session = new TrainingSession(
                UUID.randomUUID(), UUID.randomUUID(), "Welding safety", "Eng. Nsubuga", STARTS, ENDS,
                12, "Workshop", "", 50_000, true, true, NOW);

        assertEquals(50_000, session.getFee());
        assertEquals(true, session.isSafetyCourse());
        assertEquals(NOW, session.getCreatedAt());
        assertEquals(NOW, session.getUpdatedAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsABlankTitle(String title) {
        assertThrows(InvalidTrainingException.class, () -> create(title, "Eng. Nsubuga", "Lab 2", STARTS, ENDS, 20, 0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsABlankFacilitator(String facilitator) {
        assertThrows(InvalidTrainingException.class, () -> create("Laser cutting", facilitator, "Lab 2", STARTS, ENDS, 20, 0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsABlankVenue(String venue) {
        assertThrows(InvalidTrainingException.class, () -> create("Laser cutting", "Eng. Nsubuga", venue, STARTS, ENDS, 20, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5})
    void rejectsCapacityBelowOne(int capacity) {
        assertThrows(InvalidTrainingException.class,
                () -> create("Laser cutting", "Eng. Nsubuga", "Lab 2", STARTS, ENDS, capacity, 0));
    }

    @Test
    void rejectsANegativeFee() {
        assertThrows(InvalidTrainingException.class,
                () -> create("Laser cutting", "Eng. Nsubuga", "Lab 2", STARTS, ENDS, 20, -1));
    }

    @Test
    void rejectsAnEndThatIsNotAfterTheStart() {
        assertThrows(InvalidTrainingException.class,
                () -> create("Laser cutting", "Eng. Nsubuga", "Lab 2", STARTS, STARTS, 20, 0));
        assertThrows(InvalidTrainingException.class,
                () -> create("Laser cutting", "Eng. Nsubuga", "Lab 2", STARTS, STARTS.minusSeconds(1), 20, 0));
    }

    @Test
    void rejectsAMissingDepartmentOrStartTime() {
        assertThrows(InvalidTrainingException.class, () -> new TrainingSession(
                UUID.randomUUID(), null, "Laser cutting", "Eng. Nsubuga", STARTS, ENDS,
                20, "Lab 2", "", 0, false, true, NOW));
        assertThrows(InvalidTrainingException.class,
                () -> create("Laser cutting", "Eng. Nsubuga", "Lab 2", null, ENDS, 20, 0));
    }

    private static TrainingSession session(boolean published) {
        return new TrainingSession(
                UUID.randomUUID(), UUID.randomUUID(), "Laser cutting", "Eng. Nsubuga", STARTS, ENDS,
                20, "Lab 2", "Basics", 0, false, published, NOW);
    }

    private static TrainingSession create(
            String title, String facilitator, String venue, Instant startsAt, Instant endsAt, int capacity, int fee) {
        return new TrainingSession(
                UUID.randomUUID(), UUID.randomUUID(), title, facilitator, startsAt, endsAt,
                capacity, venue, "", fee, false, true, NOW);
    }
}
