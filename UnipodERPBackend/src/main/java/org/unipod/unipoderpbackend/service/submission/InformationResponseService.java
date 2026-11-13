package org.unipod.unipoderpbackend.service.submission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.dto.submission.ReplySubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemKind;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.storage.DocumentStorage;

/**
 * The submitter's answer to a "more information" request: upload the requested
 * files, record the reply note, and return the brief to the reception queue.
 */
@Service
public class InformationResponseService {
    private static final Logger log = LoggerFactory.getLogger(InformationResponseService.class);
    private static final int MAX_NOTE_LENGTH = 2000;

    private final SubmissionRepository submissions;
    private final MissingItemRepository missingItems;
    private final UploadedDocumentService uploads;
    private final SubmissionService submissionService;
    private final DocumentStorage storage;

    public InformationResponseService(
            SubmissionRepository submissions,
            MissingItemRepository missingItems,
            UploadedDocumentService uploads,
            SubmissionService submissionService,
            DocumentStorage storage
    ) {
        this.submissions = submissions;
        this.missingItems = missingItems;
        this.uploads = uploads;
        this.submissionService = submissionService;
        this.storage = storage;
    }

    @Transactional
    public SubmissionResponse respond(
            UUID submissionId,
            String note,
            List<UUID> documentItemIds,
            List<MultipartFile> files,
            Authentication authentication
    ) {
        String actor = authentication.getName();
        Submission submission = submissions.findById(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        if (!submission.getSubmitterId().equals(actor)) {
            throw new AccessDeniedException("Submitters may only respond to their own submissions.");
        }
        SubmissionStage stage = submission.getStage();
        if (stage != SubmissionStage.AWAITING_INFORMATION) {
            throw new SubmissionConflictException(
                    "Only an awaiting-information submission can be responded to."
            );
        }
        String cleanNote = clean(note);
        if (cleanNote != null && cleanNote.length() > MAX_NOTE_LENGTH) {
            throw new InvalidSubmissionException(
                    "The reply note must be 2000 characters or fewer."
            );
        }
        List<MultipartFile> suppliedFiles = files == null
                ? List.of()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        boolean hasFiles = !suppliedFiles.isEmpty();
        if (cleanNote == null && !hasFiles) {
            throw new InvalidSubmissionException("Add the missing files or a reply note.");
        }
        List<UUID> itemIds = documentItemIds == null ? List.of() : documentItemIds;
        if (hasFiles && itemIds.size() != suppliedFiles.size()) {
            throw new InvalidSubmissionException(
                    "Choose the requested item each uploaded file answers."
            );
        }

        List<MissingItem> openDocuments = missingItems
                .findAllBySubmissionIdAndKindAndStatusOrderByRequestedAtAscIdAsc(
                        submissionId,
                        MissingItemKind.DOCUMENT,
                        MissingItemStatus.OPEN
                );
        Map<UUID, MissingItem> openById = new HashMap<>();
        for (MissingItem item : openDocuments) {
            openById.put(item.getId(), item);
        }
        List<String> uploadedReferences = new ArrayList<>();
        try {
            for (int index = 0; index < suppliedFiles.size(); index++) {
                UUID itemId = itemIds.get(index);
                MissingItem requestedItem = itemId == null ? null : openById.get(itemId);
                if (requestedItem == null) {
                    throw new InvalidSubmissionException(
                            "The selected requested item is not open on this brief."
                    );
                }
                DocumentType documentType = requestedItem.getDocumentType() == null
                        ? DocumentType.OTHER
                        : requestedItem.getDocumentType();
                MultipartFile file = suppliedFiles.get(index);
                String name = documentName(file, index + 1);
                DocumentResponse uploaded = uploads.upload(
                        submissionId,
                        name,
                        documentType,
                        DocumentStatus.SUBMITTED,
                        file,
                        actor,
                        true,
                        itemId
                );
                uploadedReferences.add(uploaded.fileReference());
                openById.remove(itemId);
            }
            return submissionService.reply(
                    submissionId,
                    new ReplySubmissionRequest(
                            null, null, null, null, null, null, null, null,
                            null, null, null, null, cleanNote
                    ),
                    actor
            );
        } catch (RuntimeException exception) {
            // The reply joins this transaction, so a later failure rolls back the
            // document rows but not objects already written to storage.
            deleteUploadedObjects(uploadedReferences, exception);
            throw exception;
        }
    }

    private void deleteUploadedObjects(List<String> references, RuntimeException cause) {
        for (String reference : references) {
            try {
                storage.delete(reference);
            } catch (RuntimeException cleanupFailure) {
                cause.addSuppressed(cleanupFailure);
                log.warn("Failed to clean up stored object {}", reference, cleanupFailure);
            }
        }
    }

    private static String documentName(MultipartFile file, int position) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return "Response file " + position;
        }
        int separator = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        String name = separator >= 0 ? original.substring(separator + 1) : original;
        return name.length() <= 255 ? name : name.substring(0, 255);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
