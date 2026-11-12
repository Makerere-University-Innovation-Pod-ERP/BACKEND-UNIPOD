package org.unipod.unipoderpbackend.model.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.unipod.unipoderpbackend.exception.training.InvalidTrainingException;
import org.unipod.unipoderpbackend.exception.training.TrainingConflictException;

class MentorshipFollowUpTest {
    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-20T08:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 12, 5);

    @Test
    void startsPendingWithNormalPriorityAndEmptyNotesByDefault() {
        MentorshipFollowUp followUp = new MentorshipFollowUp(
                UUID.randomUUID(), "Prototype check-in", "Amina", "Dr Okello", DATE, "Prototype", null, null, NOW);

        assertEquals(FollowUpStatus.PENDING_FOLLOW_UP, followUp.getStatus());
        assertEquals(FollowUpPriority.NORMAL, followUp.getPriority());
        assertEquals("", followUp.getNotes());
        assertEquals(DATE, followUp.getScheduledDate());
        assertEquals(NOW, followUp.getCreatedAt());
    }

    @Test
    void keepsAGivenPriorityAndNotesAndTrimsText() {
        MentorshipFollowUp followUp = new MentorshipFollowUp(
                UUID.randomUUID(), " Prototype check-in ", " Amina ", " Dr Okello ", DATE, " Prototype ",
                FollowUpPriority.URGENT, " Bring the demo ", NOW);

        assertEquals("Prototype check-in", followUp.getTitle());
        assertEquals("Amina", followUp.getMentee());
        assertEquals("Dr Okello", followUp.getMentor());
        assertEquals("Prototype", followUp.getFocus());
        assertEquals(FollowUpPriority.URGENT, followUp.getPriority());
        assertEquals("Bring the demo", followUp.getNotes());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsBlankRequiredText(String blank) {
        assertThrows(InvalidTrainingException.class, () -> create(blank, "Amina", "Dr Okello", "Prototype"));
        assertThrows(InvalidTrainingException.class, () -> create("Check-in", blank, "Dr Okello", "Prototype"));
        assertThrows(InvalidTrainingException.class, () -> create("Check-in", "Amina", blank, "Prototype"));
        assertThrows(InvalidTrainingException.class, () -> create("Check-in", "Amina", "Dr Okello", blank));
    }

    @Test
    void rejectsAMissingDate() {
        assertThrows(InvalidTrainingException.class, () -> new MentorshipFollowUp(
                UUID.randomUUID(), "Check-in", "Amina", "Dr Okello", null, "Prototype", null, null, NOW));
    }

    @ParameterizedTest
    @EnumSource(FollowUpStatus.class)
    void anOpenFollowUpCanMoveToAnyStatus(FollowUpStatus target) {
        MentorshipFollowUp followUp = open();

        followUp.update(target, null, LATER);

        assertEquals(target, followUp.getStatus());
        assertEquals(LATER, followUp.getUpdatedAt());
    }

    @Test
    void aStatusOnlyUpdateKeepsTheExistingNotes() {
        MentorshipFollowUp followUp = new MentorshipFollowUp(
                UUID.randomUUID(), "Check-in", "Amina", "Dr Okello", DATE, "Prototype", null, "First notes", NOW);

        followUp.update(FollowUpStatus.SCHEDULED, null, LATER);

        assertEquals("First notes", followUp.getNotes());
    }

    @Test
    void givenNotesReplaceTheOldOnesAndBlankNotesClearThem() {
        MentorshipFollowUp followUp = open();

        followUp.update(FollowUpStatus.ACTION_REQUIRED, " Send the report ", LATER);
        assertEquals("Send the report", followUp.getNotes());

        followUp.update(FollowUpStatus.ACTION_REQUIRED, "   ", LATER);
        assertEquals("", followUp.getNotes());
    }

    @Test
    void rejectsAMissingStatus() {
        assertThrows(InvalidTrainingException.class, () -> open().update(null, "notes", LATER));
    }

    @ParameterizedTest
    @EnumSource(FollowUpStatus.class)
    void aCompletedFollowUpCannotBeChanged(FollowUpStatus target) {
        MentorshipFollowUp followUp = open();
        followUp.update(FollowUpStatus.COMPLETED, "Done", LATER);

        TrainingConflictException conflict = assertThrows(TrainingConflictException.class,
                () -> followUp.update(target, "Changed", LATER));

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("Done", followUp.getNotes());
        assertEquals(FollowUpStatus.COMPLETED, followUp.getStatus());
    }

    private static MentorshipFollowUp open() {
        return create("Check-in", "Amina", "Dr Okello", "Prototype");
    }

    private static MentorshipFollowUp create(String title, String mentee, String mentor, String focus) {
        return new MentorshipFollowUp(UUID.randomUUID(), title, mentee, mentor, DATE, focus, null, null, NOW);
    }
}
