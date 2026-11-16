package org.unipod.unipoderpbackend.service.operations;

import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.operations.MediaDeliverRequest;
import org.unipod.unipoderpbackend.dto.operations.MediaRequestCreateRequest;
import org.unipod.unipoderpbackend.exception.operations.OperationsNotFoundException;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.operations.MediaRequest;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.operations.MediaRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

@Service
public class MediaRequestService {
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");
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
    public Map<String, Object> create(MediaRequestCreateRequest request, String actor) {
        Instant now = clock.instant();
        int year = LocalDate.ofInstant(now, KAMPALA).getYear();
        long seq = mediaRequests.nextCodeSeq();
        String code = "MRQ-" + year + "-" + String.format("%06d", seq);
        MediaRequest mr = new MediaRequest(UUID.randomUUID(), code, request.briefId(), request.departmentOrderId(),
                request.submitterId().trim(), request.title().trim(), now);
        mediaRequests.saveAndFlush(mr);
        auditTrail.record(actor, "media_request.created", "media_request", mr.getId(), null,
                AuditTrail.details("code", code, "status", mr.getStatus()));
        return toResponse(mr);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status, UUID briefId) {
        List<MediaRequest> items;
        if (briefId != null) items = mediaRequests.findAllByBriefIdOrderByUpdatedAtDesc(briefId);
        else if (status != null && !status.isBlank()) items = mediaRequests.findAllByStatusOrderByUpdatedAtDesc(status.trim().toUpperCase());
        else items = mediaRequests.findAllByOrderByUpdatedAtDesc();
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        return mediaRequests.findById(id).map(this::toResponse)
                .orElseThrow(() -> new OperationsNotFoundException("Media request not found."));
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

        String briefStatus = null;
        if (mr.getBriefId() != null) {
            var subOpt = submissions.findByIdForUpdate(mr.getBriefId());
            if (subOpt.isPresent()) {
                var s = subOpt.get();
                try { s.advanceTo(SubmissionStage.COMPLETED, now); submissions.save(s); } catch (Exception ignored) {}
                briefStatus = s.getStage().name();
                notifySubmitter(s.getSubmitterId(), mr, request.note_to_submitter());
            }
        } else {
            notifySubmitter(mr.getSubmitterId(), mr, request.note_to_submitter());
        }
        auditTrail.record(actor, "media_request.delivered", "media_request", mr.getId(), null,
                AuditTrail.details("status", mr.getStatus(), "deliveredAt", now.toString()));
        Map<String, Object> out = toResponse(mr);
        out.put("briefStatus", briefStatus == null ? "COMPLETED" : briefStatus);
        return out;
    }

    private Map<String, Object> toResponse(MediaRequest mr) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", mr.getId()); out.put("code", mr.getCode());
        out.put("briefId", mr.getBriefId()); out.put("departmentOrderId", mr.getDepartmentOrderId());
        out.put("submitterId", mr.getSubmitterId()); out.put("title", mr.getTitle());
        out.put("status", mr.getStatus()); out.put("deliveredAt", mr.getDeliveredAt());
        out.put("deliveredBy", mr.getDeliveredBy());
        Object payload = null;
        if (mr.getDeliveryPayload() != null && !mr.getDeliveryPayload().isBlank()) {
            try { payload = mapper.readTree(mr.getDeliveryPayload()); } catch (Exception e) { payload = mr.getDeliveryPayload(); }
        }
        out.put("deliveryPayload", payload);
        return out;
    }

    private void notifySubmitter(String submitterId, MediaRequest mr, String note) {
        if (submitterId == null || !submitterId.contains("@")) return;
        try {
            notifications.publish(new NotificationEvent(NotificationType.PROJECT_CONFIRMED,
                    Set.of(submitterId), "Media delivery: " + mr.getCode(),
                    note == null || note.isBlank() ? "Your media request has been delivered." : note,
                    "media_request", mr.getId()));
        } catch (Exception ignored) {}
    }
}
