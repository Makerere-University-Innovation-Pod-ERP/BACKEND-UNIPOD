package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.unipod.unipoderpbackend.dto.submission.CreateDocumentRequest;
import org.unipod.unipoderpbackend.dto.submission.ReplySubmissionRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.dashboard.PipelineMetricsRepository;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionCodeAliasRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import org.unipod.unipoderpbackend.util.CaseCodeGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionServiceReplyTest {
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final SubmissionDocumentRepository documents = mock(SubmissionDocumentRepository.class);
    private final CaseStageHistoryRepository history = mock(CaseStageHistoryRepository.class);
    private final MissingItemRepository missingItems = mock(MissingItemRepository.class);
    private final NotificationOutbox outbox = mock(NotificationOutbox.class);
    private final RefinementRequestService refinements = mock(RefinementRequestService.class);
    private final SubmissionService service = new SubmissionService(
            submissions,
            mock(SubmissionCodeAliasRepository.class),
            history,
            mock(CaseCodeGenerator.class),
            documents,
            missingItems,
            outbox,
            mock(PipelineMetricsRepository.class),
            mock(SectorService.class),
            mock(ReceptionChecklistService.class),
            refinements,
            mock(AuditTrail.class),
            "reception@example.test",
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void improvedVersionNotificationSaysItIsAwaitingCoordinatorReview() {
        Submission submission = submission(SubmissionStage.IMPROVEMENT_REQUESTED);
        RefinementRequest request = mock(RefinementRequest.class);
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(refinements.findOpenRequest(CASE_ID)).thenReturn(Optional.of(request));

        var result = service.reply(CASE_ID, reply("Added recent field data"), "owner");

        assertEquals(SubmissionStage.RESUBMITTED, result.stage());
        verify(refinements).recordResponse(submission, request, "owner", "Added recent field data", NOW);
        verify(outbox).recordSubmitterNotification(
                eq(CASE_ID),
                eq("submission.improved_version_received"),
                eq("GEN-2026-000001"),
                eq("owner@example.test"),
                eq("Improved version received for submission GEN-2026-000001"),
                eq("Your improved version has been received and is awaiting coordinator review."),
                eq("owner"),
                eq(NOW)
        );
        verify(outbox, times(1)).recordSubmitterNotification(
                any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any()
        );
        verify(outbox, never()).recordSubmitterNotification(
                eq(CASE_ID),
                anyString(),
                eq("GEN-2026-000001"),
                eq("reception@example.test"),
                anyString(),
                anyString(),
                eq("owner"),
                eq(NOW)
        );
    }

    @Test
    void legacyImprovementRequestWithoutRefinementRowCanStillBeAnswered() {
        Submission submission = submission(SubmissionStage.IMPROVEMENT_REQUESTED);
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(refinements.findOpenRequest(CASE_ID)).thenReturn(Optional.empty());

        var result = service.reply(CASE_ID, reply("Added recent field data"), "owner");

        assertEquals(SubmissionStage.RESUBMITTED, result.stage());
        verify(refinements, never()).recordResponse(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void informationReplyStillReturnsToReception() {
        Submission submission = submission(SubmissionStage.AWAITING_INFORMATION);
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        var result = service.reply(CASE_ID, reply("Provided the missing detail"), "owner");

        assertEquals(SubmissionStage.SUBMITTED, result.stage());
        verify(outbox).recordSubmitterNotification(
                eq(CASE_ID),
                eq("submission.reply_received"),
                eq("GEN-2026-000001"),
                eq("owner@example.test"),
                eq("Reply received for submission GEN-2026-000001"),
                eq("Your reply has been received and the brief is back with reception."),
                eq("owner"),
                eq(NOW)
        );
        verify(outbox).recordSubmitterNotification(
                eq(CASE_ID),
                eq("submission.reply_received"),
                eq("GEN-2026-000001"),
                eq("reception@example.test"),
                eq("Reply received for GEN-2026-000001"),
                eq("The submitter replied with the requested information; the brief is back in the reception queue."),
                eq("owner"),
                eq(NOW)
        );
    }

    @Test
    void submitterCanAttachAnImprovedVersionDocument() {
        Submission submission = submission(SubmissionStage.IMPROVEMENT_REQUESTED);
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        var result = service.addDocument(
                CASE_ID,
                new CreateDocumentRequest(
                        "Updated project brief",
                        DocumentType.PROJECT_BRIEF,
                        DocumentStatus.SUBMITTED,
                        "improved-project-brief"
                ),
                "owner",
                true
        );

        assertEquals(1, result.version());
        assertEquals(DocumentStatus.SUBMITTED, result.status());
        var savedDocument = org.mockito.ArgumentCaptor.forClass(
                org.unipod.unipoderpbackend.model.submission.SubmissionDocument.class
        );
        verify(documents).save(savedDocument.capture());
        assertEquals(DocumentType.PROJECT_BRIEF, savedDocument.getValue().getDocumentType());
        assertEquals(1, savedDocument.getValue().getVersion());
        verify(documents).flush();
    }

    private static ReplySubmissionRequest reply(String note) {
        return new ReplySubmissionRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, note
        );
    }

    private static Submission submission(SubmissionStage stage) {
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
                "owner@example.test",
                NOW.minusSeconds(30)
        );
        ReflectionTestUtils.setField(submission, "stage", stage);
        ReflectionTestUtils.setField(submission, "code", "GEN-2026-000001");
        return submission;
    }
}
