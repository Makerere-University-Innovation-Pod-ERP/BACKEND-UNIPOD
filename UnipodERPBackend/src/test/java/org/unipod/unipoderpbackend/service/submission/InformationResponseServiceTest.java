package org.unipod.unipoderpbackend.service.submission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.dto.submission.ReplySubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.storage.DocumentStorage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InformationResponseServiceTest {
    private static final String SUBMITTER = "submitter@example.test";
    private static final UUID SUBMISSION_ID = UUID.randomUUID();

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final MissingItemRepository missingItems = mock(MissingItemRepository.class);
    private final UploadedDocumentService uploads = mock(UploadedDocumentService.class);
    private final SubmissionService submissionService = mock(SubmissionService.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final Authentication authentication = mock(Authentication.class);
    private final InformationResponseService service = new InformationResponseService(
            submissions,
            missingItems,
            uploads,
            submissionService,
            storage
    );

    @BeforeEach
    void setUp() {
        Submission submission = mock(Submission.class);
        when(submission.getSubmitterId()).thenReturn(SUBMITTER);
        when(submission.getStage()).thenReturn(SubmissionStage.AWAITING_INFORMATION);
        when(submissions.findById(SUBMISSION_ID)).thenReturn(Optional.of(submission));
        when(authentication.getName()).thenReturn(SUBMITTER);
    }

    @Test
    void requiresTheRequestedItemEachFileAnswers() {
        stubOpenDocuments(documentItem(DocumentType.RECOMMENDATION_LETTER));

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.respond(SUBMISSION_ID, null, List.of(), List.of(file("letter.pdf")), authentication)
        );
        verify(uploads, never()).upload(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void rejectsAnItemThatIsNotOpenOnTheBrief() {
        stubOpenDocuments(documentItem(DocumentType.RECOMMENDATION_LETTER));

        assertThrows(
                InvalidSubmissionException.class,
                () -> service.respond(
                        SUBMISSION_ID,
                        null,
                        List.of(UUID.randomUUID()),
                        List.of(file("letter.pdf")),
                        authentication
                )
        );
        verify(uploads, never()).upload(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void takesTheDocumentTypeFromTheSelectedItem() {
        MissingItem evidence = documentItem(DocumentType.SUPPORTING_EVIDENCE);
        UUID evidenceId = evidence.getId();
        stubOpenDocuments(documentItem(DocumentType.RECOMMENDATION_LETTER), evidence);
        when(uploads.upload(
                eq(SUBMISSION_ID),
                any(),
                eq(DocumentType.SUPPORTING_EVIDENCE),
                eq(DocumentStatus.SUBMITTED),
                any(),
                eq(SUBMITTER),
                eq(true),
                eq(evidenceId)
        )).thenReturn(mock(DocumentResponse.class));
        when(submissionService.reply(eq(SUBMISSION_ID), any(ReplySubmissionRequest.class), eq(SUBMITTER)))
                .thenReturn(mock(SubmissionResponse.class));

        service.respond(
                SUBMISSION_ID,
                null,
                List.of(evidenceId),
                List.of(file("evidence.pdf")),
                authentication
        );

        verify(uploads).upload(
                eq(SUBMISSION_ID),
                any(),
                eq(DocumentType.SUPPORTING_EVIDENCE),
                eq(DocumentStatus.SUBMITTED),
                any(),
                eq(SUBMITTER),
                eq(true),
                eq(evidenceId)
        );
        verify(submissionService).reply(eq(SUBMISSION_ID), any(ReplySubmissionRequest.class), eq(SUBMITTER));
    }

    @Test
    void acceptsAnInformationItemThatTheSubmitterAnswersWithAFile() {
        MissingItem requested = informationItem();
        UUID requestedId = requested.getId();
        stubOpenDocuments(requested);
        when(uploads.upload(
                eq(SUBMISSION_ID),
                any(),
                eq(DocumentType.OTHER),
                eq(DocumentStatus.SUBMITTED),
                any(),
                eq(SUBMITTER),
                eq(true),
                eq(requestedId)
        )).thenReturn(mock(DocumentResponse.class));
        when(submissionService.reply(eq(SUBMISSION_ID), any(ReplySubmissionRequest.class), eq(SUBMITTER)))
                .thenReturn(mock(SubmissionResponse.class));

        service.respond(
                SUBMISSION_ID,
                null,
                List.of(requestedId),
                List.of(file("details.pdf")),
                authentication
        );

        verify(uploads).upload(
                eq(SUBMISSION_ID),
                any(),
                eq(DocumentType.OTHER),
                eq(DocumentStatus.SUBMITTED),
                any(),
                eq(SUBMITTER),
                eq(true),
                eq(requestedId)
        );
    }

    @Test
    void aNoteOnlyReplyNeedsNoRequestedItem() {
        when(submissionService.reply(eq(SUBMISSION_ID), any(ReplySubmissionRequest.class), eq(SUBMITTER)))
                .thenReturn(mock(SubmissionResponse.class));

        service.respond(SUBMISSION_ID, "Attached copies will follow.", null, List.of(), authentication);

        verify(submissionService).reply(eq(SUBMISSION_ID), any(ReplySubmissionRequest.class), eq(SUBMITTER));
        verify(uploads, never()).upload(any(), any(), any(), any(), any(), any(), anyBoolean(), any());
    }

    private void stubOpenDocuments(MissingItem... items) {
        when(missingItems.findAllBySubmissionIdAndStatusOrderByRequestedAtAscIdAsc(
                SUBMISSION_ID,
                MissingItemStatus.OPEN
        )).thenReturn(List.of(items));
    }

    private static MissingItem documentItem(DocumentType documentType) {
        MissingItem item = mock(MissingItem.class);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getDocumentType()).thenReturn(documentType);
        return item;
    }

    private static MissingItem informationItem() {
        MissingItem item = mock(MissingItem.class);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getDocumentType()).thenReturn(null);
        return item;
    }

    private static MultipartFile file(String name) {
        return new MockMultipartFile("files", name, "application/pdf", new byte[] {1, 2, 3});
    }
}
