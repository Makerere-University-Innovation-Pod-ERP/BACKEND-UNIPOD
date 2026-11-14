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
     * reviewers. Design formats (DXF, DWG, STL, SVG) are included for design
     * jobs; browsers often report them as {@code application/octet-stream}, so
     * an extension fallback is applied for that content type.
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
            "image/tiff",
            "image/svg+xml",
            "application/acad",
            "application/dxf",
            "application/x-dxf",
            "image/vnd.dxf",
            "application/dwg",
            "application/x-dwg",
            "image/vnd.dwg",
            "model/stl",
            "application/sla",
            "application/vnd.ms-pki.stl"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "txt", "csv", "zip",
            "jpg", "jpeg", "png", "gif", "webp", "tif", "tiff",
            "svg", "dxf", "dwg", "stl"
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
        return upload(submissionId, name, documentType, status, file, actor, submitter, null);
    }

    /**
     * @param missingItemId the open requested item this file answers. When
     *                      supplied, only that item is resolved instead of
     *                      every open item with the same document type.
     */
    public DocumentResponse upload(
            UUID submissionId,
            String name,
            DocumentType documentType,
            DocumentStatus status,
            MultipartFile file,
            String actor,
            boolean submitter,
            UUID missingItemId
    ) {
        if (file == null || file.isEmpty() || file.getSize() > maxFileSize) {
            throw new InvalidSubmissionException("The uploaded file is empty or exceeds the allowed size.");
        }
        String documentName = name == null ? "" : name.trim();
        if (documentName.isEmpty() || documentName.length() > 255 || documentType == null || status == null) {
            throw new InvalidSubmissionException("Document details are invalid.");
        }
        SubmissionService.requireSubmitterDocumentStatus(status, submitter);
        String contentType = requireAllowedContentType(file.getContentType(), file.getOriginalFilename());
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
                if (status == DocumentStatus.SUBMITTED || status == DocumentStatus.VERIFIED) {
                    resolveMissingDocuments(submissionId, documentType, documentName, actor, now, missingItemId);
                }
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
        if (submitter && !submission.getSubmitterId().equals(actor)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Submitters may only attach documents to their own submissions."
            );
        }
        SubmissionStage stage = submission.getStage();
        if (stage == SubmissionStage.COMPLETED || stage == SubmissionStage.CLOSED) {
            throw new SubmissionConflictException("The submission cannot be changed in its current state.");
        }
        if (submitter
                && stage != SubmissionStage.DRAFT
                && stage != SubmissionStage.SUBMITTED
                && stage != SubmissionStage.AWAITING_INFORMATION
                && stage != SubmissionStage.IMPROVEMENT_REQUESTED) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Submitters may only attach documents while the brief can still change."
            );
        }
    }

    private void resolveMissingDocuments(
            UUID submissionId,
            DocumentType documentType,
            String documentName,
            String actor,
            Instant now,
            UUID missingItemId
    ) {
        if (missingItemId != null) {
            MissingItem item = missingItems.findByIdAndSubmissionId(missingItemId, submissionId)
                    .orElseThrow(() -> new InvalidSubmissionException(
                            "The requested item this file answers is no longer available."
                    ));
            if (item.getStatus() != MissingItemStatus.OPEN) {
                throw new InvalidSubmissionException(
                        "The requested item this file answers has already been resolved."
                );
            }
            item.resolve(actor, "Document uploaded: " + documentName, now);
            missingItems.save(item);
            return;
        }
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

    private static String requireAllowedContentType(String contentType, String originalFilename) {
        if (contentType == null || contentType.isBlank()) {
            return hasAllowedExtension(originalFilename)
                    ? DEFAULT_CONTENT_TYPE
                    : unsupportedFileType();
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        if (parameters >= 0) {
            normalized = normalized.substring(0, parameters).trim();
        }
        if (normalized.length() > 255
                || !normalized.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")) {
            throw new InvalidSubmissionException("The uploaded file type is not supported.");
        }
        if (ALLOWED_CONTENT_TYPES.contains(normalized)) {
            return normalized;
        }
        if (DEFAULT_CONTENT_TYPE.equals(normalized) && hasAllowedExtension(originalFilename)) {
            return normalized;
        }
        return unsupportedFileType();
    }

    private static String unsupportedFileType() {
        throw new InvalidSubmissionException("The uploaded file type is not supported.");
    }

    private static boolean hasAllowedExtension(String filename) {
        if (filename == null) {
            return false;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(filename.substring(dot + 1).trim().toLowerCase(Locale.ROOT));
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
