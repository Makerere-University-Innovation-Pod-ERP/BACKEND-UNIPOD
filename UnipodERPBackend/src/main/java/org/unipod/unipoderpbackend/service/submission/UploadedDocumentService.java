package org.unipod.unipoderpbackend.service.submission;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.exception.storage.DocumentStorageException;
import org.unipod.unipoderpbackend.exception.storage.StorageObjectNotFoundException;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.storage.S3DocumentStorage;

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
    private final TransactionTemplate transaction;

    public UploadedDocumentService(
            SubmissionRepository submissions,
            SubmissionDocumentRepository documents,
            S3DocumentStorage storage,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${storage.s3.max-file-size}") long maxFileSize
    ) {
        if (maxFileSize <= 0) {
            throw new IllegalStateException("S3_MAX_FILE_BYTES must be greater than zero.");
        }
        this.submissions = submissions;
        this.documents = documents;
        this.storage = storage;
        this.clock = clock;
        this.maxFileSize = maxFileSize;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public DocumentResponse upload(
            UUID submissionId,
            String name,
            DocumentStatus status,
            MultipartFile file,
            String actor
    ) {
        return upload(submissionId, name, DocumentType.OTHER, status, file, actor, false);
    }

    public DocumentResponse upload(
            UUID submissionId,
            String name,
            DocumentType documentType,
            DocumentStatus status,
            MultipartFile file,
            String actor,
            boolean submitter
    ) {
        if (file == null || file.isEmpty() || file.getSize() > maxFileSize) {
            throw new InvalidSubmissionException("The uploaded file is empty or exceeds the allowed size.");
        }
        String documentName = name == null ? "" : name.trim();
        if (documentName.isEmpty() || documentName.length() > 255 || documentType == null || status == null) {
            throw new InvalidSubmissionException("Document details are invalid.");
        }
        SubmissionService.requireSubmitterDocumentStatus(status, submitter);
        String originalFilename = safeFilename(file.getOriginalFilename(), documentName);
        transaction.execute(statusTransaction -> {
            Submission submission = submissions.findByIdForUpdate(submissionId)
                    .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
            SubmissionService.requireSubmitterOwnership(submission, actor, submitter);
            if (submission.getStage() == SubmissionStage.COMPLETED) {
                throw new SubmissionConflictException("The submission cannot be changed in its current state.");
            }
            return null;
        });

        String reference;
        try {
            reference = storage.upload(file);
        } catch (IOException exception) {
            throw new DocumentStorageException(exception);
        }

        String contentType = safeContentType(file.getContentType());
        try {
            SubmissionDocument document = transaction.execute(statusTransaction -> {
                Submission submission = submissions.findByIdForUpdate(submissionId)
                        .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
                SubmissionService.requireSubmitterOwnership(submission, actor, submitter);
                Instant now = clock.instant();
                submission.touch(now);
                return documents.saveAndFlush(new SubmissionDocument(
                        UUID.randomUUID(),
                        submissionId,
                        documentName,
                        documentType,
                        status,
                        reference,
                        contentType,
                        file.getSize(),
                        originalFilename,
                        actor,
                        now
                ));
            });
            return DocumentResponse.from(document);
        } catch (RuntimeException exception) {
            deleteAfterFailure(reference, exception);
            throw exception;
        }
    }

    public DownloadedDocument download(UUID submissionId, UUID documentId) {
        SubmissionDocument document = transaction.execute(status -> documents
                .findByIdAndSubmissionId(documentId, submissionId)
                .orElseThrow(StorageObjectNotFoundException::new));
        return new DownloadedDocument(document, storage.download(document.getFileReference()));
    }

    private void deleteAfterFailure(String reference, RuntimeException originalException) {
        try {
            storage.delete(reference);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
            log.warn("Unable to remove uploaded object after database write failure: {}", reference, cleanupException);
        }
    }

    private static String safeContentType(String contentType) {
        if (contentType == null || contentType.length() > 255
                || !contentType.matches("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")) {
            return DEFAULT_CONTENT_TYPE;
        }
        return contentType;
    }

    private static String safeFilename(String originalFilename, String fallback) {
        String filename = originalFilename == null ? "" : originalFilename;
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (separator >= 0) {
            filename = filename.substring(separator + 1);
        }
        filename = filename.replaceAll("[\\p{Cntrl}]+", " ").trim();
        if (filename.isEmpty()) {
            return fallback;
        }
        return filename.length() <= 255 ? filename : filename.substring(0, 255);
    }

    public record DownloadedDocument(SubmissionDocument document, java.io.InputStream content) {
    }
}
