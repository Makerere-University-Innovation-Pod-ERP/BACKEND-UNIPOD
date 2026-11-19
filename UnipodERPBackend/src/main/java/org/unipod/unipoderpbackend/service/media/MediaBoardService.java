package org.unipod.unipoderpbackend.service.media;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.media.MediaBoardRequest;
import org.unipod.unipoderpbackend.dto.media.UpdateMediaDetailsRequest;
import org.unipod.unipoderpbackend.exception.media.MediaNotFoundException;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.media.MediaBoardStatus;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.MediaRequest;
import org.unipod.unipoderpbackend.repository.operations.MediaRequestRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.notification.NotificationService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;

/**
 * Changes made from the media board. Each change locks the request row first, so two people moving the
 * same card are served one after the other and the second one sees it already moved.
 * Audit entries hold ids, statuses and flags only; the assignee and the description are free text about
 * people and stay out of the log (D-021).
 */
@Service
public class MediaBoardService {
    private static final String ENTITY = "media_request";
    private static final Pattern EMAIL_IDENTITY = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final MediaRequestRepository requests;
    private final DepartmentAccess access;
    private final AuditService audit;
    private final NotificationService notifications;
    private final Clock clock;

    public MediaBoardService(
            MediaRequestRepository requests,
            DepartmentAccess access,
            AuditService audit,
            NotificationService notifications,
            Clock clock) {
        this.requests = requests;
        this.access = access;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional
    public MediaBoardRequest changeStatus(UUID id, MediaBoardStatus target, Actor actor) {
        access.requireManage(actor, DepartmentCode.MULTIMEDIA);
        MediaRequest request = lock(id);
        String before = request.getStatus();
        request.moveTo(target, actor.email(), clock.instant());
        requests.saveAndFlush(request);
        audit.log(AuditEvent.changed(actor, "media_request.status_changed", ENTITY, id,
                Map.of("status", before), Map.of("status", request.getStatus())));
        if (target == MediaBoardStatus.DELIVERED) {
            notifySubmitter(request);
        }
        return view(request);
    }

    @Transactional
    public MediaBoardRequest updateDetails(UUID id, UpdateMediaDetailsRequest change, Actor actor) {
        access.requireManage(actor, DepartmentCode.MULTIMEDIA);
        MediaRequest request = lock(id);
        if (change.studioId() != null && !requests.studioExists(change.studioId())) {
            throw new InvalidOperationsRequestException("Choose an existing studio.");
        }
        UUID studio = change.studioId() == null ? request.getStudioId() : change.studioId();
        String assignee = change.assignee() == null ? request.getAssignee() : blankToNull(change.assignee());
        Instant date = change.date() == null ? request.getScheduledAt() : change.date().toInstant().truncatedTo(ChronoUnit.MICROS);
        String description = change.description() == null ? request.getDescription() : blankToNull(change.description());

        boolean studioChanged = !Objects.equals(studio, request.getStudioId());
        boolean assigneeChanged = !Objects.equals(assignee, request.getAssignee());
        boolean dateChanged = !Objects.equals(date, request.getScheduledAt());
        boolean descriptionChanged = !Objects.equals(description, request.getDescription());
        if (!(studioChanged || assigneeChanged || dateChanged || descriptionChanged)) {
            return view(request);
        }
        UUID previousStudio = request.getStudioId();
        request.assign(studio, assignee, date, description, clock.instant());
        requests.saveAndFlush(request);
        audit.log(AuditEvent.changed(actor, "media_request.details_changed", ENTITY, id,
                previousStudio == null ? Map.of() : Map.of("studioId", previousStudio.toString()),
                Map.of("studioChanged", studioChanged,
                        "assigneeChanged", assigneeChanged,
                        "dateChanged", dateChanged,
                        "descriptionChanged", descriptionChanged)));
        return view(request);
    }

    private MediaRequest lock(UUID id) {
        return requests.findByIdForUpdate(id).orElseThrow(MediaNotFoundException::request);
    }

    private MediaBoardRequest view(MediaRequest request) {
        String studio = request.getStudioId() == null
                ? null
                : requests.findStudioName(request.getStudioId()).orElse(null);
        return MediaBoardRequest.from(request, studio, clock.getZone());
    }

    // Same message the submitter gets when staff deliver through the submitter-facing endpoint.
    private void notifySubmitter(MediaRequest request) {
        String submitter = request.getSubmitterId();
        if (submitter == null || !EMAIL_IDENTITY.matcher(submitter).matches()) {
            return;
        }
        notifications.publish(new NotificationEvent(NotificationType.MEDIA_DELIVERED, Set.of(submitter),
                "Media delivery: " + request.getCode(), "Your media request has been delivered.",
                ENTITY, request.getId()));
    }

    private static String blankToNull(String value) {
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
