package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequestedChange;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefinementRequestServiceTest {
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private final RefinementRequestRepository requests = mock(RefinementRequestRepository.class);
    private final RefinementRequestedChangeRepository changes = mock(RefinementRequestedChangeRepository.class);
    private final CaseRevisionRepository revisions = mock(CaseRevisionRepository.class);
    private final RefinementRequestService service = new RefinementRequestService(
            requests, changes, revisions, Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void askCreatesANumberedRequestAndOrderedRequestedChanges() {
        Submission submission = submission();
        when(requests.existsByBriefIdAndStatus(CASE_ID, RefinementStatus.OPEN)).thenReturn(false);
        when(requests.countByBriefId(CASE_ID)).thenReturn(2L);
        when(requests.saveAndFlush(any(RefinementRequest.class))).thenAnswer(invocation -> {
            RefinementRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", 23L);
            return request;
        });
        List<RefinementRequestedChange> savedChanges = new ArrayList<>();
        when(changes.saveAll(any())).thenAnswer(invocation -> {
            Iterable<RefinementRequestedChange> requestedChanges = invocation.getArgument(0);
            requestedChanges.forEach(savedChanges::add);
            return savedChanges;
        });
        when(changes.findByRequestIdOrderByPositionAsc(23L)).thenReturn(List.of(
                new RefinementRequestedChange(23L, 1, "Add measurement dates"),
                new RefinementRequestedChange(23L, 2, "Attach the latest survey")
        ));
        var result = service.ask(
                submission,
                RefinementSource.COORDINATOR,
                "Please add current field data",
                LocalDate.of(2026, 11, 15),
                List.of("Add measurement dates", "Attach the latest survey"),
                "coordinator",
                NOW
        );

        assertEquals(3, result.cycle());
        assertEquals(23L, result.id());
        assertEquals(List.of("Add measurement dates", "Attach the latest survey"), result.requestedChanges());
        verify(changes).saveAll(any());
        assertEquals(2, savedChanges.size());
        RefinementRequestedChange first = savedChanges.get(0);
        RefinementRequestedChange second = savedChanges.get(1);
        assertEquals(23L, first.getRequestId());
        assertEquals(1, first.getPosition());
        assertEquals("Add measurement dates", first.getText());
        assertEquals(23L, second.getRequestId());
        assertEquals(2, second.getPosition());
        assertEquals("Attach the latest survey", second.getText());
    }

    @Test
    void askRejectsASecondOpenRequest() {
        Submission submission = submission();
        when(requests.existsByBriefIdAndStatus(CASE_ID, RefinementStatus.OPEN)).thenReturn(true);

        assertThrows(SubmissionConflictException.class, () -> service.ask(
                submission,
                RefinementSource.COORDINATOR,
                "Please add detail",
                LocalDate.of(2026, 11, 15),
                List.of("Add detail"),
                "coordinator",
                NOW
        ));
    }

    @Test
    void askRejectsInvalidGuidanceWithoutSaving() {
        Submission submission = submission();

        assertThrows(InvalidSubmissionException.class, () -> service.ask(
                submission,
                RefinementSource.COORDINATOR,
                "   ",
                LocalDate.of(2026, 11, 15),
                List.of("Add detail"),
                "coordinator",
                NOW
        ));

        verifyNoInteractions(requests, changes, revisions);
    }

    @Test
    void askRejectsPastDueDateWithoutSaving() {
        Submission submission = submission();

        assertThrows(InvalidSubmissionException.class, () -> service.ask(
                submission,
                RefinementSource.COORDINATOR,
                "Please add detail",
                LocalDate.of(2026, 11, 7),
                List.of("Add detail"),
                "coordinator",
                NOW
        ));

        verifyNoInteractions(requests, changes, revisions);
    }

    @Test
    void responseClosesRequestAndRecordsTheImprovedVersion() {
        Submission submission = submission();
        ReflectionTestUtils.setField(submission, "briefVersion", 2);
        RefinementRequest request = new RefinementRequest(
                CASE_ID,
                1,
                RefinementSource.COORDINATOR,
                "Add detail",
                LocalDate.of(2026, 11, 15),
                "coordinator",
                NOW
        );
        ReflectionTestUtils.setField(request, "id", 9L);

        service.recordResponse(submission, request, "submitter", "Added measurement results", NOW.plusSeconds(5));

        assertEquals(RefinementStatus.RESPONDED, request.getStatus());
        assertEquals(NOW.plusSeconds(5), request.getRespondedAt());
        verify(requests).save(request);
        var revisionCaptor = org.mockito.ArgumentCaptor.forClass(CaseRevision.class);
        verify(revisions).save(revisionCaptor.capture());
        CaseRevision revision = revisionCaptor.getValue();
        assertEquals(CASE_ID, revision.getBriefId());
        assertEquals(2, revision.getVersionNumber());
        assertEquals(9L, revision.getRequestId());
        assertEquals("Added measurement results", revision.getWhatChanged());
        assertEquals("submitter", revision.getSubmittedBy());
        assertEquals(NOW.plusSeconds(5), revision.getSubmittedAt());
    }

    @Test
    void responseRejectsRequestFromAnotherCaseWithoutSaving() {
        Submission submission = submission();
        RefinementRequest request = new RefinementRequest(
                UUID.randomUUID(),
                1,
                RefinementSource.COORDINATOR,
                "Add detail",
                LocalDate.of(2026, 11, 15),
                "coordinator",
                NOW
        );
        ReflectionTestUtils.setField(request, "id", 9L);

        assertThrows(SubmissionConflictException.class, () -> service.recordResponse(
                submission, request, "submitter", "Added measurement results", NOW.plusSeconds(5)
        ));

        verify(requests, never()).save(any(RefinementRequest.class));
        verify(revisions, never()).save(any(CaseRevision.class));
    }

    @Test
    void responseRejectsBlankSummaryWithoutSaving() {
        Submission submission = submission();
        RefinementRequest request = new RefinementRequest(
                CASE_ID,
                1,
                RefinementSource.COORDINATOR,
                "Add detail",
                LocalDate.of(2026, 11, 15),
                "coordinator",
                NOW
        );
        ReflectionTestUtils.setField(request, "id", 9L);

        assertThrows(InvalidSubmissionException.class, () -> service.recordResponse(
                submission, request, "submitter", "  ", NOW.plusSeconds(5)
        ));

        verify(requests, never()).save(any(RefinementRequest.class));
        verify(revisions, never()).save(any(CaseRevision.class));
    }

    private static Submission submission() {
        Submission submission = new Submission(
                CASE_ID,
                SubmissionCategory.GENERAL,
                "Title",
                "Purpose",
                "Problem",
                "owner",
                "owner",
                null,
                null,
                null,
                null,
                NOW
        );
        ReflectionTestUtils.setField(submission, "stage", SubmissionStage.IMPROVEMENT_REQUESTED);
        return submission;
    }
}
