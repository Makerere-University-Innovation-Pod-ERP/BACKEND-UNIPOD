package org.unipod.unipoderpbackend.service.submission;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemKind;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.storage.DocumentStorage;

@Service
public class UploadedDocumentService {
    private static final Logger log = LoggerFactory.getLogger(UploadedDocumentService.class);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /**
     * Document types reception accepts. Anything outside this list is rejected
     * rather than silently stored, because uploaded files are streamed back to
     * reviewers.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/rtf",
            "application/zip",
            "text/plain",
            "text/csv",
            "text/rtf",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/tiff"
    );

    private final SubmissionRepository submissions;
    private final SubmissionDocumentRepository documents;
    private final MissingItemRepository missingItems;
    private final DocumentStorage storage;
    private final Clock clock;
    private final long maxFileSize;
    private final TransactionTemplate transaction;

    public UploadedDocumentService(
            SubmissionRepository submissions,
            SubmissionDocumentRepository documents,
            MissingItemRepository missingItems,
            DocumentStorage storage,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${storage.s3.max-file-size}") long maxFileSize
    ) {
        if (maxFileSize <= 0) {
            throw new IllegalStateException("The storage file size limit must be greater than zero.");
        }
        this.submissions = submissions;
        this.documents = documents;
        this.missingItems = missingItems;
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
        String contentType = requireAllowedContentType(file.getContentType());
        String originalFilename = safeFilename(file.getOriginalFilename(), documentName);
        transaction.execute(statusTransaction -> {
            Submission submission = submissions.findByIdForUpdate(submissionId)
                    .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
            requireDocumentStage(submission, actor, submitter);
            return null;
        });

        String reference;
        try {
            reference = storage.upload(file);
        } catch (IOException exception) {
            throw new DocumentStorageException(exception);
        }

        try {
            SubmissionDocument document = transaction.execute(statusTransaction -> {
                Submission submission = submissions.findByIdForUpdate(submissionId)
                        .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
                requireDocumentStage(submission, actor, submitter);
                Instant now = clock.instant();
                submission.touch(now);
                long version = documents.maxVersion(submissionId, documentType) + 1;
                SubmissionDocument pending = new SubmissionDocument(
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
                );
                pending.assignVersion(version);
                SubmissionDocument saved = documents.saveAndFlush(pending);
                resolveMissingDocuments(submissionId, documentType, documentName, actor, now);
                return saved;
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

    private static void requireDocumentStage(Submission submission, String actor, boolean submitter) {
        SubmissionStage stage = submission.getStage();
        if (stage == SubmissionStage.COMPLETED || stage == SubmissionStage.REJECTED) {
            throw new SubmissionConflictException("The submission cannot be changed in its current state.");
        }
        if (submitter) {
            if (!submission.getSubmitterId().equals(actor)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Submitters may only attach documents to their own submissions."
                );
            }
            if (stage != SubmissionStage.DRAFT
                    && stage != SubmissionStage.SUBMITTED
                    && stage != SubmissionStage.AWAITING_INFORMATION) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Submitters may only attach documents before the submission is forwarded."
                );
            }
        }
    }

    private void resolveMissingDocuments(
            UUID submissionId,
            DocumentType documentType,
            String documentName,
            String actor,
            Instant now
    ) {
        List<MissingItem> openItems = missingItems
                .findAllBySubmissionIdAndKindAndDocumentTypeAndStatus(
                        submissionId,
                        MissingItemKind.DOCUMENT,
                        documentType,
                        MissingItemStatus.OPEN
                );
        if (openItems.isEmpty()) {
            return;
        }
        for (MissingItem item : openItems) {
            item.resolve(actor, "Document uploaded: " + documentName, now);
        }
        missingItems.saveAll(openItems);
    }

    private void deleteAfterFailure(String reference, RuntimeException originalException) {
        try {
            storage.delete(reference);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
            log.warn("Unable to remove uploaded object after database write failure: {}", reference, cleanupException);
        }
    }

    private static String requireAllowedContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 255
                || !normalized.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) {
            throw new InvalidSubmissionException("The uploaded file type is not supported.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(normalized)) {
            throw new InvalidSubmissionException("The uploaded file type is not supported.");
        }
        return normalized;
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
