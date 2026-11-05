package org.unipod.unipoderpbackend.submissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "storage.s3.enabled", havingValue = "true")
public class UploadedDocumentService {
    private static final Logger log = LoggerFactory.getLogger(UploadedDocumentService.class);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final SubmissionRepository submissions;
    private final SubmissionDocumentRepository documents;
    private final S3DocumentStorage storage;
    private final Clock clock;
    private final long maxFileSize;

    public UploadedDocumentService(
            SubmissionRepository submissions,
            SubmissionDocumentRepository documents,
            S3DocumentStorage storage,
            @Value("${storage.s3.max-file-size}") long maxFileSize
    ) {
        if (maxFileSize <= 0) {
            throw new IllegalStateException("S3_MAX_FILE_BYTES must be greater than zero.");
        }
        this.submissions = submissions;
        this.documents = documents;
        this.storage = storage;
        this.maxFileSize = maxFileSize;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public DocumentResponse upload(
            UUID submissionId,
            String name,
            DocumentStatus status,
            MultipartFile file,
            String actor
    ) {
        Submission submission = submissions.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        if (file == null || file.isEmpty() || file.getSize() > maxFileSize) {
            throw new InvalidSubmissionException("The uploaded file is empty or exceeds the allowed size.");
        }
        String documentName = name == null ? "" : name.trim();
        if (documentName.isEmpty() || documentName.length() > 255 || status == null) {
            throw new InvalidSubmissionException("Document details are invalid.");
        }

        String reference;
        try {
            reference = storage.upload(file);
        } catch (IOException exception) {
            throw new DocumentStorageException(exception);
        }

        Instant now = clock.instant();
        submission.touch(now);
        String contentType = safeContentType(file.getContentType());
        SubmissionDocument document = new SubmissionDocument(
                UUID.randomUUID(),
                submissionId,
                documentName,
                status,
                reference,
                contentType,
                file.getSize(),
                actor,
                now
        );
        try {
            documents.saveAndFlush(document);
        } catch (RuntimeException exception) {
            deleteAfterFailure(reference, exception);
            throw exception;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int completionStatus) {
                if (completionStatus == STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(reference);
                    } catch (RuntimeException exception) {
                        log.warn("Unable to remove uploaded object after database rollback: {}", reference);
                    }
                }
            }
        });
        return DocumentResponse.from(document);
    }

    @Transactional(readOnly = true)
    public DownloadedDocument download(UUID submissionId, UUID documentId) {
        SubmissionDocument document = documents.findByIdAndSubmissionId(documentId, submissionId)
                .orElseThrow(StorageObjectNotFoundException::new);
        return new DownloadedDocument(document, storage.download(document.getFileReference()));
    }

    private void deleteAfterFailure(String reference, RuntimeException originalException) {
        try {
            storage.delete(reference);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
            log.warn("Unable to remove uploaded object after database write failure: {}", reference);
        }
    }

    private static String safeContentType(String contentType) {
        if (contentType == null || !contentType.matches("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")) {
            return DEFAULT_CONTENT_TYPE;
        }
        return contentType;
    }

    public record DownloadedDocument(SubmissionDocument document, java.io.InputStream content) {
    }
}
