package org.unipod.unipoderpbackend.service.operations;

import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.operations.MediaDeliverRequest;
import org.unipod.unipoderpbackend.exception.operations.OperationsNotFoundException;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.operations.MediaRequest;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.operations.MediaRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

@Service
public class MediaRequestService {
    private final MediaRequestRepository mediaRequests;
    private final SubmissionRepository submissions;
    private final NotificationService notifications;
    private final AuditTrail auditTrail;
    private final ObjectMapper mapper;
    private final Clock clock;
    public MediaRequestService(MediaRequestRepository mediaRequests, SubmissionRepository submissions,
                               NotificationService notifications, AuditTrail auditTrail, ObjectMapper mapper, Clock clock) {
        this.mediaRequests = mediaRequests; this.submissions = submissions; this.notifications = notifications;
        this.auditTrail = auditTrail; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public Map<String, Object> deliver(UUID id, MediaDeliverRequest request, String actor) {
        MediaRequest mr = mediaRequests.findByIdForUpdate(id)
                .orElseThrow(() -> new OperationsNotFoundException("Media request not found."));
        Instant now = clock.instant();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("delivery_links", request.delivery_links() == null ? List.of() : request.delivery_links());
        payload.put("file_ids", request.file_ids() == null ? List.of() : request.file_ids());
        payload.put("note_to_submitter", request.note_to_submitter());
        String payloadJson;
        try { payloadJson = mapper.writeValueAsString(payload); } catch (Exception e) { payloadJson = "{}"; }
        mr.deliver(payloadJson, actor, now);
        mr.complete(now);
        mediaRequests.saveAndFlush(mr);

        // if linked to a brief/submission, move to COMPLETED and notify submitter
        if (mr.getBriefId() != null) {
            submissions.findByIdForUpdate(mr.getBriefId()).ifPresent(s -> {
                try {
                    s.advanceTo(SubmissionStage.COMPLETED, now);
                    submissions.save(s);
                } catch (Exception ignored) {
                    // stage may not be advanceable from current state in this flow; still record delivery
                }
                notifySubmitter(s.getSubmitterId(), mr, request.note_to_submitter(), now);
            });
        } else {
            notifySubmitter(mr.getSubmitterId(), mr, request.note_to_submitter(), now);
        }
        auditTrail.record(actor, "media_request.delivered", "media_request", mr.getId(), null,
                AuditTrail.details("status", mr.getStatus(), "deliveredAt", now.toString()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", mr.getId()); out.put("status", mr.getStatus()); out.put("delivered_at", mr.getDeliveredAt());
        out.put("delivery", payload); out.put("briefStatus", "COMPLETED");
        return out;
    }

    private void notifySubmitter(String submitterId, MediaRequest mr, String note, Instant now) {
        if (submitterId == null || !submitterId.contains("@")) return;
        try {
            notifications.publish(new NotificationEvent(NotificationType.PROJECT_CONFIRMED,
                    Set.of(submitterId), "Media delivery: " + mr.getCode(),
                    note == null || note.isBlank() ? "Your media request has been delivered." : note,
                    "media_request", mr.getId()));
        } catch (Exception ignored) {}
    }
}

