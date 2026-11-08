package org.unipod.unipoderpbackend.service.submission;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.exception.storage.StorageObjectNotFoundException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.storage.S3DocumentStorage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UploadedDocumentServiceTest {
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final SubmissionDocumentRepository documents = mock(SubmissionDocumentRepository.class);
    private final S3DocumentStorage storage = mock(S3DocumentStorage.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private UploadedDocumentService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new UploadedDocumentService(
                submissions, documents, storage, transactionManager, 10_000
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
    void downloadMissingMetadataIsNotFound() {
        UUID submissionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(documents.findByIdAndSubmissionId(documentId, submissionId)).thenReturn(Optional.empty());

        assertThrows(StorageObjectNotFoundException.class, () -> service.download(submissionId, documentId));
        verifyNoInteractions(storage);
    }

    private static MultipartFile file() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(4L);
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getOriginalFilename()).thenReturn("brief.txt");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        return file;
    }
}
