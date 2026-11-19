package org.unipod.unipoderpbackend.service.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.unipod.unipoderpbackend.dto.improvement.RefinementQueueRow;
import org.unipod.unipoderpbackend.dto.improvement.RefinementQueueState;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.RefinementQueueEntry;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;

/** The row mapping and the service's own role check, without a database. */
class RefinementQueueServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 10);
    private static final UUID BRIEF = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Test
    void aNullCodeGivesANullTrackingReference() {
        RefinementQueueRow row = RefinementQueueService.rowOf(entry(null, SubmissionStage.IMPROVEMENT_REQUESTED,
                RefinementStatus.OPEN, TODAY.plusDays(1)), TODAY);

        assertNull(row.trackingReference());
        assertEquals(BRIEF, row.briefId());
        assertEquals("Brief", row.title());
        assertEquals(SubmissionCategory.GENERAL, row.briefType());
    }

    @Test
    void anOpenRequestIsWaitingAndOverdueOnlyBeforeToday() {
        RefinementQueueRow yesterday = RefinementQueueService.rowOf(entry("C-1", SubmissionStage.IMPROVEMENT_REQUESTED,
                RefinementStatus.OPEN, TODAY.minusDays(1)), TODAY);
        RefinementQueueRow today = RefinementQueueService.rowOf(entry("C-1", SubmissionStage.IMPROVEMENT_REQUESTED,
                RefinementStatus.OPEN, TODAY), TODAY);

        assertEquals(RefinementQueueState.WAITING_FOR_SUBMITTER, yesterday.state());
        assertTrue(yesterday.overdue());
        assertEquals(RefinementQueueState.WAITING_FOR_SUBMITTER, today.state());
        assertFalse(today.overdue());
        assertEquals("C-1", today.trackingReference());
        assertEquals(2, today.cycle());
        assertEquals(RefinementSource.FACT_FINDING, today.source());
        assertEquals(TODAY, today.dueDate());
    }

    @Test
    void resubmittedIsReadyToAcceptAndNeverOverdue() {
        RefinementQueueRow row = RefinementQueueService.rowOf(entry("C-2", SubmissionStage.RESUBMITTED,
                RefinementStatus.RESPONDED, TODAY.minusDays(30)), TODAY);

        assertEquals(RefinementQueueState.READY_TO_ACCEPT, row.state());
        assertFalse(row.overdue());
        assertEquals(2, row.cycle());
        assertEquals(TODAY.minusDays(30), row.dueDate());
    }

    @Test
    void improvementRequestedWithoutAWaitingRequestNeedsARequestWithNoRequestValues() {
        RefinementQueueRow answered = RefinementQueueService.rowOf(entry("C-3", SubmissionStage.IMPROVEMENT_REQUESTED,
                RefinementStatus.RESPONDED, TODAY.minusDays(5)), TODAY);
        RefinementQueueRow none = RefinementQueueService.rowOf(new RefinementQueueEntry(BRIEF, "Brief", "C-4",
                SubmissionCategory.GENERAL, SubmissionStage.IMPROVEMENT_REQUESTED, Instant.EPOCH,
                null, null, null, null, null), TODAY);

        for (RefinementQueueRow row : List.of(answered, none)) {
            assertEquals(RefinementQueueState.NEEDS_REQUEST, row.state());
            assertNull(row.cycle());
            assertNull(row.source());
            assertNull(row.dueDate());
            assertFalse(row.overdue());
        }
    }

    @Test
    void theServiceRejectsOtherRolesBeforeReadingAnything() {
        RefinementRequestRepository requests = mock(RefinementRequestRepository.class);
        RefinementQueueService service = new RefinementQueueService(requests, Clock.systemUTC());

        for (String role : List.of("ROLE_REVIEWER", "ROLE_SUBMITTER", "ROLE_RECEPTIONIST")) {
            assertThrows(AccessDeniedException.class, () -> service.queue(PageRequest.of(0, 25),
                    new TestingAuthenticationToken("someone@example.test", null, role)));
        }
        assertThrows(AccessDeniedException.class, () -> service.queue(PageRequest.of(0, 25), null));
        verifyNoInteractions(requests);
    }

    private static RefinementQueueEntry entry(String code, SubmissionStage stage, RefinementStatus status,
            LocalDate dueDate) {
        return new RefinementQueueEntry(BRIEF, "Brief", code, SubmissionCategory.GENERAL, stage, Instant.EPOCH,
                7L, 2, RefinementSource.FACT_FINDING, dueDate, status);
    }
}
