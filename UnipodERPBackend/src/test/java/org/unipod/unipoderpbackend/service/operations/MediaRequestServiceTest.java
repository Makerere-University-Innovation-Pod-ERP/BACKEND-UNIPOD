package org.unipod.unipoderpbackend.service.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unipod.unipoderpbackend.dto.operations.MediaRequestCreateRequest;
import org.unipod.unipoderpbackend.dto.submission.CaseTransitionRequest;
import org.unipod.unipoderpbackend.model.operations.MediaRequest;
import org.unipod.unipoderpbackend.model.submission.CaseStage;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.operations.DepartmentOrderRepository;
import org.unipod.unipoderpbackend.repository.operations.MediaRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.notification.NotificationService;
import org.unipod.unipoderpbackend.service.submission.CaseFileService;
import tools.jackson.databind.ObjectMapper;

class MediaRequestServiceTest {
    private static final UUID REQUEST_ID = UUID.randomUUID();
    private static final UUID BRIEF_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private static final Authentication ADMIN = new UsernamePasswordAuthenticationToken(
            "admin@example.org", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR")));

    private final MediaRequestRepository mediaRequests = mock(MediaRequestRepository.class);
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final CaseFileService caseFiles = mock(CaseFileService.class);
    private final MediaRequestService service = new MediaRequestService(
            mediaRequests,
            submissions,
            caseFiles,
            mock(DepartmentOrderRepository.class),
            mock(NotificationService.class),
            mock(AuditTrail.class),
            mock(ObjectMapper.class),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void createStoresNormalizedBriefOwnerInsteadOfCallerSpelling() {
        Submission submission = mock(Submission.class);
        when(submission.getSubmitterId()).thenReturn("Owner@Example.org");
        when(submissions.findById(BRIEF_ID)).thenReturn(Optional.of(submission));
        when(mediaRequests.nextCodeSeq()).thenReturn(1L);

        Map<String, Object> response = service.create(
                new MediaRequestCreateRequest(BRIEF_ID, null, "OWNER@example.org", "Media support"),
                "coordinator@example.org");

        assertEquals("owner@example.org", response.get("submitterId"));
    }

    @Test
    void staffListAppliesBriefAndStatusFiltersTogether() {
        when(mediaRequests.findAllByBriefIdAndStatusOrderByUpdatedAtDesc(BRIEF_ID, "DELIVERED"))
                .thenReturn(List.of());

        service.list("delivered", BRIEF_ID, null, true);

        verify(mediaRequests).findAllByBriefIdAndStatusOrderByUpdatedAtDesc(BRIEF_ID, "DELIVERED");
    }

    @Test
    void completingRequestLeavesBriefInProgressWhileAnotherRequestIsOpen() {
        MediaRequest request = deliveredRequest();
        Submission submission = inProgressSubmission();
        when(mediaRequests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        when(submissions.findByIdForUpdate(BRIEF_ID)).thenReturn(Optional.of(submission));
        when(mediaRequests.existsByBriefIdAndIdNotAndStatusNotIn(eq(BRIEF_ID), eq(REQUEST_ID), any()))
                .thenReturn(true);

        Map<String, Object> response = service.complete(REQUEST_ID, ADMIN);

        assertEquals("COMPLETED", response.get("status"));
        verify(caseFiles, never()).transition(any(), any(CaseTransitionRequest.class), any());
    }

    @Test
    void completingLastRequestTransitionsBriefThroughCaseFileLifecycle() {
        MediaRequest request = deliveredRequest();
        Submission submission = inProgressSubmission();
        when(mediaRequests.findByIdForUpdate(REQUEST_ID)).thenReturn(Optional.of(request));
        when(submissions.findByIdForUpdate(BRIEF_ID)).thenReturn(Optional.of(submission));
        when(mediaRequests.existsByBriefIdAndIdNotAndStatusNotIn(eq(BRIEF_ID), eq(REQUEST_ID), any()))
                .thenReturn(false);

        service.complete(REQUEST_ID, ADMIN);

        verify(caseFiles).transition(
                eq(BRIEF_ID),
                eq(new CaseTransitionRequest(CaseStage.COMPLETED, null, null, null, 4L)),
                eq(ADMIN));
    }

    private static MediaRequest deliveredRequest() {
        MediaRequest request = new MediaRequest(
                REQUEST_ID, "MRQ-2026-000001", BRIEF_ID, null, "owner@example.org", "Media support", NOW);
        request.deliver("{}", "coordinator@example.org", NOW);
        return request;
    }

    private static Submission inProgressSubmission() {
        Submission submission = mock(Submission.class);
        when(submission.getId()).thenReturn(BRIEF_ID);
        when(submission.getStage()).thenReturn(SubmissionStage.IN_PROGRESS);
        when(submission.getVersion()).thenReturn(4L);
        return submission;
    }
}
