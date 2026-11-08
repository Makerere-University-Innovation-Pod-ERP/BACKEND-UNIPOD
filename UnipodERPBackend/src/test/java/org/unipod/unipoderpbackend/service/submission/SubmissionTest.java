package org.unipod.unipoderpbackend.service.submission;

import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubmissionTest {
    @Test
    void registrationRequiresStudentDetails() {
        Submission submission = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.STUDENT,
                "Prototype",
                "Test a prototype",
                "Limited access to equipment",
                "staff-1",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-04T00:00:00Z")
        );

        assertThrows(
                InvalidSubmissionException.class,
                () -> submission.register("STU-2026-000001", Instant.parse("2026-11-04T01:00:00Z"), "staff-2")
        );
    }

    @Test
    void successfulRegistrationRecordsReceipt() {
        Instant receivedAt = Instant.parse("2026-11-04T01:00:00Z");
        Submission submission = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Public idea",
                "Improve access",
                "Current access is limited",
                "staff-1",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-04T00:00:00Z")
        );

        submission.register("GEN-2026-000001", receivedAt, "staff-2");

        assertEquals("GEN-2026-000001", submission.getCode());
        assertEquals(SubmissionStage.RECEIVED, submission.getStage());
        assertEquals(receivedAt, submission.getReceivedAt());
        assertEquals("staff-2", submission.getReceivingOfficer());
    }

    @Test
    void registrationCannotBeRepeated() {
        Submission submission = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Public idea",
                "Improve access",
                "Current access is limited",
                "staff-1",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-04T00:00:00Z")
        );
        submission.register("GEN-2026-000001", Instant.parse("2026-11-04T01:00:00Z"), "staff-2");

        assertThrows(
                SubmissionConflictException.class,
                () -> submission.register("GEN-2026-000002", Instant.parse("2026-11-04T02:00:00Z"), "staff-3")
        );
    }

    @Test
    void caseCodeYearUsesUgandaLocalDate() {
        Instant receivedAt = Instant.parse("2026-12-31T21:30:00Z");

        assertEquals(2027, SubmissionService.caseYear(receivedAt));
    }
}
