package org.unipod.unipoderpbackend.service.submission;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
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

/**
 * The submitter's answer to a "more information" request: upload the requested
 * files, record the reply note, and return the brief to the reception queue.
 */
@Service
public class InformationResponseService {
    private final SubmissionRepository submissions;
    private final MissingItemRepository missingItems;
    private final UploadedDocumentService uploads;
    private final SubmissionService submissionService;

    public InformationResponseService(
            SubmissionRepository submissions,
            MissingItemRepository missingItems,
            UploadedDocumentService uploads,
            SubmissionService submissionService
    ) {
        this.submissions = submissions;
        this.missingItems = missingItems;
        this.uploads = uploads;
        this.submissionService = submissionService;
    }

    @Transactional
    public SubmissionResponse respond(
            UUID submissionId,
            String note,
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
        if (stage != SubmissionStage.AWAITING_INFORMATION && stage != SubmissionStage.SUBMITTED) {
            throw new SubmissionConflictException(
                    "Only an awaiting-information submission can be responded to."
            );
        }
        String cleanNote = clean(note);
        boolean hasFiles = files != null && files.stream().anyMatch(file -> file != null && !file.isEmpty());
        if (cleanNote == null && !hasFiles) {
            throw new InvalidSubmissionException("Add the missing files or a reply note.");
        }

        List<MissingItem> openDocuments = missingItems
                .findAllBySubmissionIdAndKindAndStatusOrderByRequestedAtAscIdAsc(
                        submissionId,
                        MissingItemKind.DOCUMENT,
                        MissingItemStatus.OPEN
                );
        if (hasFiles) {
            int index = 0;
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                DocumentType documentType = index < openDocuments.size()
                        ? openDocuments.get(index).getDocumentType()
                        : DocumentType.OTHER;
                String name = documentName(file, index + 1);
                uploads.upload(
                        submissionId,
                        name,
                        documentType,
                        DocumentStatus.SUBMITTED,
                        file,
                        actor,
                        true
                );
                index++;
            }
        }
        return submissionService.reply(
                submissionId,
                new ReplySubmissionRequest(
                        null, null, null, null, null, null, null, null,
                        null, null, null, null, cleanNote
                ),
                actor
        );
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
