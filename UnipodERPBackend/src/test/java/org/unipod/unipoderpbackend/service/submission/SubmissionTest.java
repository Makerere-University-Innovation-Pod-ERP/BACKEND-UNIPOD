package org.unipod.unipoderpbackend.service.submission;

import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.springframework.security.access.AccessDeniedException;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void registrationKeepsTrackingReferenceAndClearsInformationRequest() {
        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        Instant requestedAt = Instant.parse("2026-11-04T01:00:00Z");
        Submission submission = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Public idea",
                "Improve access",
                "Current access is limited",
                "submitter@example.test",
                "submitter@example.test",
                null,
                null,
                null,
                null,
                createdAt
        );
        submission.assignTrackingReference("GEN-2026-000001");
        submission.requestInformation("Please attach a brief.", "receptionist-1", requestedAt);

        submission.register("GEN-2026-000001", requestedAt.plusSeconds(60), "receptionist-1");

        assertEquals("GEN-2026-000001", submission.getCode());
        assertEquals(SubmissionStage.RECEIVED, submission.getStage());
        assertNull(submission.getInformationRequestedNote());
        assertNull(submission.getInformationRequestedBy());
        assertNull(submission.getInformationRequestedAt());
    }

    @Test
    void draftUpdatePreservesAnOutstandingInformationRequestUntilSubmitterResponds() {
        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-11-04T02:00:00Z");
        Submission submission = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Public idea",
                "Improve access",
                "Current access is limited",
                "submitter@example.test",
                "submitter@example.test",
                null,
                null,
                null,
                null,
                createdAt
        );
        submission.requestInformation("Please attach a brief.", "receptionist-1", createdAt.plusSeconds(60));

        submission.updateDraft(
                "Updated idea",
                "Improve access",
                "Current access is limited",
                null,
                null,
                null,
                null,
                updatedAt
        );

        assertEquals("Updated idea", submission.getTitle());
        assertEquals("Please attach a brief.", submission.getInformationRequestedNote());
        assertEquals("receptionist-1", submission.getInformationRequestedBy());
        assertEquals(createdAt.plusSeconds(60), submission.getInformationRequestedAt());
    }

    @Test
    void submittersCannotMarkDocumentsAsVerified() {
        assertThrows(
                AccessDeniedException.class,
                () -> SubmissionService.requireSubmitterDocumentStatus(DocumentStatus.VERIFIED, true)
        );
        SubmissionService.requireSubmitterDocumentStatus(DocumentStatus.SUBMITTED, true);
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
    void submitRequiresPhoneOrEmail() {
        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        Submission submission = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Public idea",
                "Improve access",
                "Current access is limited",
                "staff-1",
                "staff-1",
                null,
                null,
                null,
                null,
                createdAt
        );

        assertThrows(
                InvalidSubmissionException.class,
                () -> submission.submit("GEN-2026-000001", createdAt.plusSeconds(60))
        );
        assertEquals(SubmissionStage.DRAFT, submission.getStage());
    }

    @Test
    void submitAcceptsPhoneWithoutEmail() {
        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        Submission submission = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Public idea",
                "Improve access",
                "Current access is limited",
                "staff-1",
                "staff-1",
                null,
                null,
                null,
                null,
                createdAt
        );
        submission.applyIntakeDetails("+256700000000", null, null, null, null, null);

        submission.submit("GEN-2026-000001", createdAt.plusSeconds(60));

        assertEquals(SubmissionStage.SUBMITTED, submission.getStage());
    }

    @Test
    void caseCodeYearUsesUgandaLocalDate() {
        Instant receivedAt = Instant.parse("2026-12-31T21:30:00Z");

        assertEquals(2027, SubmissionService.caseYear(receivedAt));
    }

    @Test
    void reviewerAssignmentUpdatesTimestampAndNormalizesSubject() {
        Instant createdAt = Instant.parse("2026-11-04T00:00:00Z");
        Instant assignedAt = Instant.parse("2026-11-04T01:00:00Z");
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
                createdAt
        );

        submission.assignReviewer(" reviewer-1 ", assignedAt);

        assertEquals("reviewer-1", submission.getAssignedReviewerSubject());
        assertEquals(assignedAt, submission.getUpdatedAt());
    }
}
