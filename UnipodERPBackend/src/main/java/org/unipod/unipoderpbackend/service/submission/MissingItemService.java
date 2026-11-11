package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.submission.MissingItemRequest;
import org.unipod.unipoderpbackend.dto.submission.MissingItemResponse;
import org.unipod.unipoderpbackend.dto.submission.MissingItemsRequest;
import org.unipod.unipoderpbackend.dto.submission.MissingItemsResponse;
import org.unipod.unipoderpbackend.dto.submission.ResolveMissingItemRequest;
import org.unipod.unipoderpbackend.dto.submission.SendReminderRequest;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

@Service
public class MissingItemService {
    private static final int MAX_SUMMARY_LENGTH = 2000;

    private final SubmissionRepository submissions;
    private final MissingItemRepository items;
    private final NotificationOutbox notificationOutbox;
    private final Clock clock;

    public MissingItemService(
            SubmissionRepository submissions,
            MissingItemRepository items,
            NotificationOutbox notificationOutbox,
            Clock clock
    ) {
        this.submissions = submissions;
        this.items = items;
        this.notificationOutbox = notificationOutbox;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MissingItemsResponse list(UUID submissionId) {
        requireSubmission(submissionId);
        return response(submissionId, items.findAllBySubmissionIdOrderByRequestedAtAscIdAsc(submissionId));
    }

    @Transactional
    public MissingItemsResponse addMissingDocuments(
            UUID submissionId,
            MissingItemsRequest request,
            String actor
    ) {
        Submission submission = submissions.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        requireNotCompleted(submission);
        Instant now = clock.instant();
        for (MissingItemRequest itemRequest : request.items()) {
            if (itemRequest.documentType() == null) {
                throw new InvalidSubmissionException("A document type is required for every missing document.");
            }
            items.save(MissingItem.document(
                    UUID.randomUUID(),
                    submissionId,
                    requireLabel(itemRequest.label()),
                    clean(itemRequest.note()),
                    itemRequest.documentType(),
                    actor,
                    now
            ));
        }
        submission.touch(now);
        items.flush();
        return response(submissionId, items.findAllBySubmissionIdOrderByRequestedAtAscIdAsc(submissionId));
    }

    @Transactional
    public MissingItemResponse resolve(
            UUID submissionId,
            UUID itemId,
            ResolveMissingItemRequest request,
            String actor
    ) {
        Submission submission = submissions.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        MissingItem item = items.findByIdAndSubmissionId(itemId, submissionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The missing item was not found."
                ));
        Instant now = clock.instant();
        item.resolve(actor, clean(request.note()), now);
        submission.touch(now);
        items.flush();
        return MissingItemResponse.from(item);
    }

    @Transactional
    public MissingItemsResponse sendReminder(
            UUID submissionId,
            SendReminderRequest request,
            String actor
    ) {
        Submission submission = submissions.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        requireNotCompleted(submission);
        List<MissingItem> openItems = items
                .findAllBySubmissionIdAndStatusOrderByRequestedAtAscIdAsc(
                        submissionId,
                        MissingItemStatus.OPEN
                );
        if (openItems.isEmpty()) {
            throw new SubmissionConflictException("There are no open missing items to remind about.");
        }
        if (!SubmissionService.isEmailAddress(submission.getSubmitterEmail())) {
            throw new InvalidSubmissionException(
                    "A submitter email address is required before sending a reminder."
            );
        }

        Instant now = clock.instant();
        String note = reminderNote(request.note(), openItems);
        for (MissingItem item : openItems) {
            item.markReminded(now);
        }
        items.flush();
        submission.touch(now);
        notificationOutbox.recordReminderSent(
                submission.getId(),
                submission.getCode(),
                submission.getSubmitterEmail(),
                note,
                actor,
                now
        );
        return response(submissionId, items.findAllBySubmissionIdOrderByRequestedAtAscIdAsc(submissionId));
    }

    private void requireSubmission(UUID submissionId) {
        if (!submissions.existsById(submissionId)) {
            throw new SubmissionNotFoundException(submissionId);
        }
    }

    private static void requireNotCompleted(Submission submission) {
        if (submission.getStage() == SubmissionStage.COMPLETED) {
            throw new SubmissionConflictException("Completed submissions cannot be changed.");
        }
    }

    private static MissingItemsResponse response(UUID submissionId, List<MissingItem> items) {
        long openCount = items.stream()
                .filter(item -> item.getStatus() == MissingItemStatus.OPEN)
                .count();
        return new MissingItemsResponse(
                submissionId,
                openCount,
                items.stream().map(MissingItemResponse::from).toList()
        );
    }

    private static String reminderNote(String note, List<MissingItem> openItems) {
        if (note != null && !note.isBlank()) {
            return truncate(note.trim());
        }
        return truncate(openItems.stream()
                .map(MissingItem::getLabel)
                .collect(Collectors.joining("; ")));
    }

    private static String truncate(String value) {
        return value.length() <= MAX_SUMMARY_LENGTH ? value : value.substring(0, MAX_SUMMARY_LENGTH);
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank()) {
            throw new InvalidSubmissionException("A label is required for every missing item.");
        }
        return label.trim();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
