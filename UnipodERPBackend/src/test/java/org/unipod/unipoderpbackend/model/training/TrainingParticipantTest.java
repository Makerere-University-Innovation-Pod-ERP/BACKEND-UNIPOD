package org.unipod.unipoderpbackend.model.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.unipod.unipoderpbackend.exception.training.InvalidTrainingException;
import org.unipod.unipoderpbackend.exception.training.TrainingConflictException;

class TrainingParticipantTest {
    private static final Instant REGISTERED = Instant.parse("2026-11-12T05:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-20T08:00:00Z");

    @Test
    void aFreeSessionConfirmsTheSeatImmediately() {
        TrainingParticipant participant = register(0);

        assertEquals(RegistrationStatus.CONFIRMED, participant.getStatus());
        assertTrue(participant.occupiesSeat());
    }

    @Test
    void aPaidSessionHoldsTheSeatUntilPaymentIsConfirmed() {
        TrainingParticipant participant = register(50_000);

        assertEquals(RegistrationStatus.PENDING_PAYMENT, participant.getStatus());
        assertTrue(participant.occupiesSeat());
    }

    @Test
    void startsUnmarkedAndNotPassedWithTheRegistrationTime() {
        TrainingParticipant participant = register(0);

        assertEquals(AttendanceStatus.UNMARKED, participant.getAttendance());
        assertFalse(participant.isPassed());
        assertEquals(REGISTERED, participant.getRegisteredAt());
        assertEquals(REGISTERED, participant.getUpdatedAt());
    }

    @Test
    void trimsDetailsAndTreatsABlankEmailAsMissing() {
        TrainingParticipant participant = new TrainingParticipant(
                UUID.randomUUID(), UUID.randomUUID(), "  Amina Nakato ", " 2021/HD01/0001U ", " Engineering ",
                "   ", 0, REGISTERED);

        assertEquals("Amina Nakato", participant.getName());
        assertEquals("2021/HD01/0001U", participant.getRegNumber());
        assertEquals("Engineering", participant.getDepartment());
        assertNull(participant.getContactEmail());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsABlankName(String name) {
        assertThrows(InvalidTrainingException.class, () -> new TrainingParticipant(
                UUID.randomUUID(), UUID.randomUUID(), name, "2021/HD01/0001U", "Engineering", null, 0, REGISTERED));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsABlankRegNumber(String regNumber) {
        assertThrows(InvalidTrainingException.class, () -> new TrainingParticipant(
                UUID.randomUUID(), UUID.randomUUID(), "Amina Nakato", regNumber, "Engineering", null, 0, REGISTERED));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsABlankDepartment(String department) {
        assertThrows(InvalidTrainingException.class, () -> new TrainingParticipant(
                UUID.randomUUID(), UUID.randomUUID(), "Amina Nakato", "2021/HD01/0001U", department, null, 0, REGISTERED));
    }

    @Test
    void confirmingPaymentMovesAPendingRegistrationToConfirmed() {
        TrainingParticipant participant = register(50_000);

        participant.confirmPayment(LATER);

        assertEquals(RegistrationStatus.CONFIRMED, participant.getStatus());
        assertEquals(LATER, participant.getUpdatedAt());
    }

    @Test
    void paymentCannotBeConfirmedTwice() {
        TrainingParticipant participant = register(50_000);
        participant.confirmPayment(LATER);

        assertConflict(() -> participant.confirmPayment(LATER));
    }

    @Test
    void aFreeRegistrationHasNoPaymentToConfirm() {
        assertConflict(() -> register(0).confirmPayment(LATER));
    }

    @Test
    void aCancelledRegistrationCannotBePaidFor() {
        TrainingParticipant participant = register(50_000);
        participant.cancel(LATER);

        assertConflict(() -> participant.confirmPayment(LATER));
    }

    @Test
    void cancellingFreesTheSeat() {
        TrainingParticipant participant = register(0);

        participant.cancel(LATER);

        assertEquals(RegistrationStatus.CANCELLED, participant.getStatus());
        assertFalse(participant.occupiesSeat());
        assertEquals(LATER, participant.getUpdatedAt());
    }

    @Test
    void aRegistrationAwaitingPaymentCanBeCancelled() {
        TrainingParticipant participant = register(50_000);

        participant.cancel(LATER);

        assertEquals(RegistrationStatus.CANCELLED, participant.getStatus());
    }

    @Test
    void cannotCancelTwice() {
        TrainingParticipant participant = register(0);
        participant.cancel(LATER);

        assertConflict(() -> participant.cancel(LATER));
    }

    @Test
    void cannotCancelSomeoneWhoHasPassed() {
        TrainingParticipant participant = passed();

        assertConflict(() -> participant.cancel(LATER));
    }

    @ParameterizedTest
    @EnumSource(AttendanceStatus.class)
    void recordsEveryAttendanceValueForAConfirmedParticipant(AttendanceStatus attendance) {
        TrainingParticipant participant = register(0);

        participant.markAttendance(attendance, LATER);

        assertEquals(attendance, participant.getAttendance());
        assertEquals(LATER, participant.getUpdatedAt());
    }

    @Test
    void attendanceCannotBeRecordedBeforePaymentIsConfirmed() {
        assertConflict(() -> register(50_000).markAttendance(AttendanceStatus.PRESENT, LATER));
    }

    @Test
    void attendanceCannotBeRecordedForACancelledRegistration() {
        TrainingParticipant participant = register(0);
        participant.cancel(LATER);

        assertConflict(() -> participant.markAttendance(AttendanceStatus.PRESENT, LATER));
    }

    @ParameterizedTest
    @EnumSource(value = AttendanceStatus.class, names = {"ABSENT", "EXCUSED", "UNMARKED"})
    void someoneWhoPassedCannotBeMarkedAnythingButPresent(AttendanceStatus attendance) {
        TrainingParticipant participant = passed();

        assertConflict(() -> participant.markAttendance(attendance, LATER));
        assertEquals(AttendanceStatus.PRESENT, participant.getAttendance());
    }

    @Test
    void someoneWhoPassedCanBeMarkedPresentAgain() {
        TrainingParticipant participant = passed();

        participant.markAttendance(AttendanceStatus.PRESENT, LATER);

        assertEquals(AttendanceStatus.PRESENT, participant.getAttendance());
    }

    @Test
    void passingIsRecordedForAPresentParticipantOfASafetyCourse() {
        TrainingParticipant participant = register(0);
        participant.markAttendance(AttendanceStatus.PRESENT, LATER);

        participant.markPassed(true, LATER);

        assertTrue(participant.isPassed());
    }

    @Test
    void onlySafetyCoursesRecordAPass() {
        TrainingParticipant participant = register(0);
        participant.markAttendance(AttendanceStatus.PRESENT, LATER);

        assertConflict(() -> participant.markPassed(false, LATER));
        assertFalse(participant.isPassed());
    }

    @ParameterizedTest
    @EnumSource(value = AttendanceStatus.class, names = {"ABSENT", "EXCUSED", "UNMARKED"})
    void passingNeedsThePresentMark(AttendanceStatus attendance) {
        TrainingParticipant participant = register(0);
        participant.markAttendance(attendance, LATER);

        assertConflict(() -> participant.markPassed(true, LATER));
    }

    @Test
    void passingCannotBeRecordedTwice() {
        TrainingParticipant participant = passed();

        assertConflict(() -> participant.markPassed(true, LATER));
    }

    private static TrainingParticipant register(int sessionFee) {
        return new TrainingParticipant(
                UUID.randomUUID(), UUID.randomUUID(), "Amina Nakato", "2021/HD01/0001U", "Engineering",
                "amina@students.test", sessionFee, REGISTERED);
    }

    private static TrainingParticipant passed() {
        TrainingParticipant participant = register(0);
        participant.markAttendance(AttendanceStatus.PRESENT, LATER);
        participant.markPassed(true, LATER);
        return participant;
    }

    private static void assertConflict(Runnable action) {
        TrainingConflictException conflict = assertThrows(TrainingConflictException.class, action::run);
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }
}
