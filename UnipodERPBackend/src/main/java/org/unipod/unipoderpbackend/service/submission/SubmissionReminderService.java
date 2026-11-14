package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.submission.MissingItemsResponse;
import org.unipod.unipoderpbackend.dto.submission.SendReminderRequest;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

/**
 * Sends the reception reminder through both channels the submitter sees: the
 * email outbox (handled by {@link MissingItemService}) and the in-app bell.
 */
@Service
public class SubmissionReminderService {
    private static final int MAX_TITLE_LENGTH = 200;

    private final MissingItemService missingItems;
    private final SubmissionRepository submissions;
    private final NotificationService notifications;
    private final Clock clock;

    public SubmissionReminderService(
            MissingItemService missingItems,
            SubmissionRepository submissions,
            NotificationService notifications,
            Clock clock
    ) {
        this.missingItems = missingItems;
        this.submissions = submissions;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public MissingItemsResponse remind(
            UUID submissionId,
            SendReminderRequest request,
            Authentication authentication
    ) {
        MissingItemsResponse response = missingItems.sendReminder(submissionId, request, authentication);
        Submission submission = submissions.findVisibleById(
                        submissionId,
                        SubmissionVisibility.scope(authentication),
                        authentication.getName()
                )
                .orElseThrow(() -> new SubmissionNotFoundException(submissionId));
        String recipient = submission.getSubmitterEmail();
        if (recipient != null && !recipient.isBlank()) {
            String title = "Reminder for submission " + submission.getCode();
            notifications.publish(new NotificationEvent(
                    NotificationType.DOCUMENT_REQUESTED,
                    Set.of(recipient),
                    title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH),
                    body(request),
                    "submission",
                    submissionId
            ));
        }
        return response;
    }

    private static String body(SendReminderRequest request) {
        String note = request == null ? null : request.note();
        if (note != null && !note.isBlank()) {
            return note.trim();
        }
        return "Please provide the outstanding items so reception can continue with your brief.";
    }

    /**
     * Returns the instant a reminder would use, exposed for queue previews.
     */
    public Instant now() {
        return clock.instant();
    }
}
