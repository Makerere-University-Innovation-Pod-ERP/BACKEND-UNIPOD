package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckItemRequest;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckUpdateRequest;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.submission.ReceptionCheck;
import org.unipod.unipoderpbackend.model.submission.ReceptionCheckItem;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.ReceptionCheckRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceptionChecklistServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-11T09:00:00Z");

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final ReceptionCheckRepository checks = mock(ReceptionCheckRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ReceptionChecklistService service;

    @BeforeEach
    void setUp() {
        service = new ReceptionChecklistService(submissions, checks, clock);
    }

    @Test
    void getReturnsGeneralChecklistWithoutStudentOnlyLines() {
        Submission submission = submission(SubmissionCategory.GENERAL);
        when(submissions.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(checks.findAllBySubmissionIdOrderByItemKeyAsc(submission.getId())).thenReturn(List.of());

        var response = service.get(submission.getId());

        assertEquals(6, response.totalCount());
        assertEquals(0, response.checkedCount());
        assertFalse(response.complete());
        assertTrue(response.items().stream().noneMatch(item -> item.key().equals("STUDENT_EVIDENCE")));
    }

    @Test
    void getIncludesStudentOnlyLinesForStudentSubmission() {
        Submission submission = submission(SubmissionCategory.STUDENT);
        when(submissions.findById(submission.getId())).thenReturn(Optional.of(submission));
        when(checks.findAllBySubmissionIdOrderByItemKeyAsc(submission.getId())).thenReturn(List.of());

        var response = service.get(submission.getId());

        assertEquals(8, response.totalCount());
        assertTrue(response.items().stream().anyMatch(item -> item.key().equals("RECOMMENDATION_LETTER")));
    }

    @Test
    void updateRecordsCheckedLineAndNote() {
        Submission submission = submission(SubmissionCategory.GENERAL);
        when(submissions.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));
        when(checks.findAllBySubmissionIdOrderByItemKeyAsc(submission.getId())).thenReturn(List.of());
        when(checks.save(any(ReceptionCheck.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.update(
                submission.getId(),
                new ReceptionCheckUpdateRequest(List.of(
                        new ReceptionCheckItemRequest("IDENTITY_CONFIRMED", true, " Student ID sighted ")
                )),
                "receptionist-1"
        );

        var item = response.items().stream()
                .filter(entry -> entry.key().equals("IDENTITY_CONFIRMED"))
                .findFirst()
                .orElseThrow();
        assertTrue(item.checked());
        assertEquals("Student ID sighted", item.note());
        assertEquals("receptionist-1", item.checkedBy());
        assertEquals(NOW, item.checkedAt());
        assertEquals(1, response.checkedCount());
        verify(checks).flush();
    }

    @Test
    void updateRejectsStudentOnlyLineForGeneralSubmission() {
        Submission submission = submission(SubmissionCategory.GENERAL);
        when(submissions.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));
        when(checks.findAllBySubmissionIdOrderByItemKeyAsc(submission.getId())).thenReturn(List.of());

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.update(
                        submission.getId(),
                        new ReceptionCheckUpdateRequest(List.of(
                                new ReceptionCheckItemRequest("STUDENT_EVIDENCE", true, null)
                        )),
                        "receptionist-1"
                )
        );
    }

    @Test
    void updateRejectsUnknownChecklistKey() {
        Submission submission = submission(SubmissionCategory.GENERAL);
        when(submissions.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));
        when(checks.findAllBySubmissionIdOrderByItemKeyAsc(submission.getId())).thenReturn(List.of());

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.update(
                        submission.getId(),
                        new ReceptionCheckUpdateRequest(List.of(
                                new ReceptionCheckItemRequest("NOT_A_LINE", true, null)
                        )),
                        "receptionist-1"
                )
        );
    }

    @Test
    void updateRejectsChecklistChangesAfterForwarding() {
        Submission submission = submission(SubmissionCategory.GENERAL);
        submission.register("GEN-2026-000001", NOW, "receptionist-1");
        submission.transitionTo(SubmissionStage.REGISTERED, NOW);
        submission.transitionTo(SubmissionStage.FACT_FINDING, NOW);
        when(submissions.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        assertThrows(
                SubmissionConflictException.class,
                () -> service.update(
                        submission.getId(),
                        new ReceptionCheckUpdateRequest(List.of(
                                new ReceptionCheckItemRequest("IDENTITY_CONFIRMED", true, null)
                        )),
                        "receptionist-1"
                )
        );
    }

    private static Submission submission(SubmissionCategory category) {
        return new Submission(
                UUID.randomUUID(),
                category,
                "Prototype",
                "Test a prototype",
                "Limited access to equipment",
                "staff-1",
                "submitter@example.test",
                null,
                null,
                null,
                null,
                NOW
        );
    }
}
