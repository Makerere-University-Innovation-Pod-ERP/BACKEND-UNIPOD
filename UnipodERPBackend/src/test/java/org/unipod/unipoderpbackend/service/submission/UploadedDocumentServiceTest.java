package org.unipod.unipoderpbackend.service.submission;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.exception.storage.StorageObjectNotFoundException;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemKind;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.storage.DocumentStorage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UploadedDocumentServiceTest {
    private static final Instant FIXED_TIME = Instant.parse("2026-03-10T09:30:00Z");
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final SubmissionDocumentRepository documents = mock(SubmissionDocumentRepository.class);
    private final MissingItemRepository missingItems = mock(MissingItemRepository.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Clock clock = Clock.fixed(FIXED_TIME, ZoneId.of("Africa/Kampala"));
    private UploadedDocumentService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new UploadedDocumentService(
                submissions, documents, missingItems, storage, clock, transactionManager, 10_000
        );
    }

    @Test
    void rejectsMissingSubmissionBeforeUploading() throws Exception {
        UUID id = UUID.randomUUID();
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThrows(SubmissionNotFoundException.class, () -> service.upload(
                id, "brief", DocumentStatus.SUBMITTED, file(), "actor"
        ));

        verifyNoInteractions(storage);
    }

    @Test
    void deletesObjectWhenMetadataPersistenceFails() throws Exception {
        UUID id = UUID.randomUUID();
        Submission submission = new Submission(
                id, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "actor", null, null, null, null, Instant.now()
        );
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.of(submission));
        when(storage.upload(any())).thenReturn("opaque-object");
        doThrow(new IllegalStateException("database failure"))
                .when(documents).saveAndFlush(any(SubmissionDocument.class));

        assertThrows(IllegalStateException.class, () -> service.upload(
                id, "brief", DocumentStatus.SUBMITTED, file(), "actor"
        ));

        verify(storage).delete("opaque-object");
    }

    @Test
    void uploadUsesConfiguredClockForSubmissionAndDocumentTimestamps() throws Exception {
        UUID id = UUID.randomUUID();
        Submission submission = new Submission(
                id, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "actor", null, null, null, null, Instant.parse("2026-03-01T00:00:00Z")
        );
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.of(submission));
        when(storage.upload(any())).thenReturn("opaque-object");
        when(documents.saveAndFlush(any(SubmissionDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(id, "brief", DocumentStatus.SUBMITTED, file(), "actor");

        assertEquals(FIXED_TIME, submission.getUpdatedAt());
        verify(documents).saveAndFlush(argThat(document -> FIXED_TIME.equals(document.getCreatedAt())));
    }

    @Test
    void downloadMissingMetadataIsNotFound() {
        UUID submissionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(documents.findByIdAndSubmissionId(documentId, submissionId)).thenReturn(Optional.empty());

        assertThrows(StorageObjectNotFoundException.class, () -> service.download(submissionId, documentId));
        verifyNoInteractions(storage);
    }

    @Test
    void uploadingMatchingDocumentResolvesOpenDocumentRequest() throws Exception {
        UUID id = UUID.randomUUID();
        Submission submission = new Submission(
                id, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "actor", null, null, null, null, Instant.parse("2026-03-01T00:00:00Z")
        );
        MissingItem openRequest = MissingItem.document(
                UUID.randomUUID(),
                id,
                "Project brief",
                null,
                DocumentType.PROJECT_BRIEF,
                "receptionist-1",
                FIXED_TIME
        );
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.of(submission));
        when(storage.upload(any())).thenReturn("opaque-object");
        when(documents.saveAndFlush(any(SubmissionDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(missingItems.findAllBySubmissionIdAndKindAndDocumentTypeAndStatus(
                id,
                MissingItemKind.DOCUMENT,
                DocumentType.PROJECT_BRIEF,
                MissingItemStatus.OPEN
        )).thenReturn(List.of(openRequest));

        service.upload(
                id,
                "brief",
                DocumentType.PROJECT_BRIEF,
                DocumentStatus.SUBMITTED,
                file(),
                "actor",
                false
        );

        assertEquals(MissingItemStatus.RESOLVED, openRequest.getStatus());
        assertEquals("actor", openRequest.getResolvedBy());
        verify(missingItems).saveAll(List.of(openRequest));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"layout.DWG", "plan.dxf", "model.stl", "drawing.svg"})
    void acceptsDesignFormatsReportedAsOctetStream(String filename) throws Exception {
        UUID id = UUID.randomUUID();
        Submission submission = new Submission(
                id, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "actor", null, null, null, null, Instant.parse("2026-03-01T00:00:00Z")
        );
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.of(submission));
        when(storage.upload(any())).thenReturn("opaque-object");
        when(documents.saveAndFlush(any(SubmissionDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(
                id,
                "design",
                DocumentType.SUPPORTING_EVIDENCE,
                DocumentStatus.SUBMITTED,
                file("application/octet-stream", filename),
                "actor",
                false
        );

        verify(documents, org.mockito.Mockito.atLeastOnce()).saveAndFlush(argThat(document ->
                "application/octet-stream".equals(document.getContentType())));
    }

    @Test
    void acceptsParametersOnAllowedContentTypes() throws Exception {
        UUID id = UUID.randomUUID();
        Submission submission = new Submission(
                id, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "actor", null, null, null, null, Instant.parse("2026-03-01T00:00:00Z")
        );
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.of(submission));
        when(storage.upload(any())).thenReturn("opaque-object");
        when(documents.saveAndFlush(any(SubmissionDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(
                id,
                "brief",
                DocumentType.SUPPORTING_EVIDENCE,
                DocumentStatus.SUBMITTED,
                file("text/plain; charset=UTF-8", "brief.txt"),
                "actor",
                false
        );

        verify(documents).saveAndFlush(argThat(document ->
                "text/plain".equals(document.getContentType())));
    }

    @Test
    void acceptsSvgAndStlContentTypes() throws Exception {
        UUID id = UUID.randomUUID();
        Submission submission = new Submission(
                id, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "actor", null, null, null, null, Instant.parse("2026-03-01T00:00:00Z")
        );
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.of(submission));
        when(storage.upload(any())).thenReturn("opaque-object");
        when(documents.saveAndFlush(any(SubmissionDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(id, "drawing", DocumentStatus.SUBMITTED,
                file("image/svg+xml", "drawing.svg"), "actor");
        service.upload(id, "model", DocumentStatus.SUBMITTED,
                file("model/stl", "model.stl"), "actor");
    }

    @Test
    void rejectsOctetStreamWithUnknownExtension() throws Exception {
        UUID id = UUID.randomUUID();
        Submission submission = new Submission(
                id, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "actor", null, null, null, null, Instant.parse("2026-03-01T00:00:00Z")
        );
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.of(submission));

        assertThrows(InvalidSubmissionException.class, () -> service.upload(
                id, "payload", DocumentStatus.SUBMITTED,
                file("application/octet-stream", "payload.exe"), "actor"
        ));
        verifyNoInteractions(storage);
    }

    @Test
    void submitterCanAttachARevisionWhileImprovementIsRequested() throws Exception {
        UUID id = UUID.randomUUID();
        Submission submission = new Submission(
                id, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "actor", "actor", null, null, null, Instant.parse("2026-03-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(submission, "stage", SubmissionStage.IMPROVEMENT_REQUESTED);
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.of(submission));
        when(storage.upload(any())).thenReturn("opaque-object");
        when(documents.saveAndFlush(any(SubmissionDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upload(id, "revision", DocumentType.OTHER, DocumentStatus.SUBMITTED, file(), "actor", true);

        verify(documents).saveAndFlush(any(SubmissionDocument.class));
    }

    @Test
    void submitterCannotAttachDocumentsAfterResubmitting() throws Exception {
        UUID id = UUID.randomUUID();
        Submission submission = new Submission(
                id, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "actor", "actor", null, null, null, Instant.parse("2026-03-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(submission, "stage", SubmissionStage.RESUBMITTED);
        when(submissions.findByIdForUpdate(id)).thenReturn(Optional.of(submission));

        assertThrows(AccessDeniedException.class, () -> service.upload(
                id, "revision", DocumentType.OTHER, DocumentStatus.SUBMITTED, file(), "actor", true
        ));
        verifyNoInteractions(storage);
    }

    private static MultipartFile file() throws Exception {
        return file("text/plain", "brief.txt");
    }

    private static MultipartFile file(String contentType, String filename) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(4L);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        return file;
    }
}

