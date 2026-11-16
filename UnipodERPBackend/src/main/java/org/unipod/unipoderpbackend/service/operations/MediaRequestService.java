package org.unipod.unipoderpbackend.service.operations;

import tools.jackson.core.JacksonException;
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
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.exception.operations.OperationsConflictException;
import org.unipod.unipoderpbackend.model.operations.DepartmentOrder;
import org.unipod.unipoderpbackend.repository.operations.DepartmentOrderRepository;
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
    private final DepartmentOrderRepository orders;
    private final NotificationService notifications;
    private final AuditTrail auditTrail;
    private final ObjectMapper mapper;
    private final Clock clock;
    public MediaRequestService(MediaRequestRepository mediaRequests, SubmissionRepository submissions,
                               DepartmentOrderRepository orders,
                               NotificationService notifications, AuditTrail auditTrail, ObjectMapper mapper, Clock clock) {
        this.mediaRequests = mediaRequests; this.submissions = submissions; this.orders = orders; this.notifications = notifications;
        this.auditTrail = auditTrail; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public Map<String, Object> create(MediaRequestCreateRequest request, String actor) {
        if (request.briefId() == null && request.departmentOrderId() == null) {
            throw new InvalidOperationsRequestException("A briefId or departmentOrderId is required.");
        }
        if (request.briefId() != null) {
            var submission = submissions.findById(request.briefId())
                    .orElseThrow(() -> new OperationsNotFoundException("Submission not found."));
            if (!sameIdentity(submission.getSubmitterId(), request.submitterId())) {
                throw new InvalidOperationsRequestException("submitterId must match the brief owner.");
            }
        }
        if (request.departmentOrderId() != null) {
            DepartmentOrder order = orders.findById(request.departmentOrderId())
                    .orElseThrow(() -> new OperationsNotFoundException("Department order not found."));
            if (!sameIdentity(order.getCustomerId(), request.submitterId())) {
                throw new InvalidOperationsRequestException("submitterId must match the department order customer.");
            }
        }
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
    public List<Map<String, Object>> list(String status, UUID briefId, String submitterId, boolean staff) {
        List<MediaRequest> items;
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (staff) {
            if (briefId != null) items = mediaRequests.findAllByBriefIdOrderByUpdatedAtDesc(briefId);
            else if (normalizedStatus != null) items = mediaRequests.findAllByStatusOrderByUpdatedAtDesc(normalizedStatus);
            else items = mediaRequests.findAllByOrderByUpdatedAtDesc();
        } else if (briefId != null && normalizedStatus != null) {
            items = mediaRequests.findAllBySubmitterIdAndBriefIdAndStatusOrderByUpdatedAtDesc(
                    submitterId, briefId, normalizedStatus);
        } else if (briefId != null) {
            items = mediaRequests.findAllBySubmitterIdAndBriefIdOrderByUpdatedAtDesc(submitterId, briefId);
        } else if (normalizedStatus != null) {
            items = mediaRequests.findAllBySubmitterIdAndStatusOrderByUpdatedAtDesc(submitterId, normalizedStatus);
        } else {
            items = mediaRequests.findAllBySubmitterIdOrderByUpdatedAtDesc(submitterId);
        }
        return items.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id, String submitterId, boolean staff) {
        MediaRequest request = mediaRequests.findById(id)
                .orElseThrow(() -> new OperationsNotFoundException("Media request not found."));
        if (!staff && !sameIdentity(request.getSubmitterId(), submitterId)) {
            throw new OperationsNotFoundException("Media request not found.");
        }
        return toResponse(request);
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
        try {
            payloadJson = mapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new InvalidOperationsRequestException(
                    "OPERATIONS_INVALID", "Could not serialize media delivery data.", exception);
        }
        mr.deliver(payloadJson, actor, now);
        mediaRequests.saveAndFlush(mr);

        String briefStatus = null;
        UUID boardCardSubmissionId = mr.getBriefId();
        if (mr.getBriefId() != null) {
            var s = submissions.findByIdForUpdate(mr.getBriefId())
                    .orElseThrow(() -> new OperationsNotFoundException("Submission not found."));
            briefStatus = s.getStage().name();
            boardCardSubmissionId = s.getId();
        }
        notifySubmitter(mr.getSubmitterId(), mr, request.note_to_submitter());
        auditTrail.record(actor, "media_request.delivered", "media_request", mr.getId(), null,
                AuditTrail.details("status", mr.getStatus(), "deliveredAt", now.toString()));
        Map<String, Object> out = toResponse(mr);
        out.put("briefStatus", briefStatus);
        out.put("boardCard", boardCardSubmissionId == null ? null
                : Map.of("submissionId", boardCardSubmissionId, "status", briefStatus));
        return out;
    }

    @Transactional
    public Map<String, Object> complete(UUID id, String actor) {
        MediaRequest mr = mediaRequests.findByIdForUpdate(id)
                .orElseThrow(() -> new OperationsNotFoundException("Media request not found."));
        Instant now = clock.instant();
        if (mr.getBriefId() != null) {
            var submission = submissions.findByIdForUpdate(mr.getBriefId())
                    .orElseThrow(() -> new OperationsNotFoundException("Submission not found."));
            if (submission.getStage() != SubmissionStage.IN_PROGRESS) {
                throw new OperationsConflictException("INVALID_SUBMISSION_TRANSITION",
                        "The linked submission must be in progress before media delivery can complete it.");
            }
            submission.advanceTo(SubmissionStage.COMPLETED, now);
            submissions.save(submission);
        }
        mr.complete(now);
        mediaRequests.saveAndFlush(mr);
        auditTrail.record(actor, "media_request.completed", "media_request", mr.getId(),
                AuditTrail.details("status", "DELIVERED"),
                AuditTrail.details("status", mr.getStatus()));
        return toResponse(mr);
    }

    private Map<String, Object> toResponse(MediaRequest mr) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", mr.getId()); out.put("code", mr.getCode());
        out.put("briefId", mr.getBriefId()); out.put("departmentOrderId", mr.getDepartmentOrderId());
        out.put("submitterId", mr.getSubmitterId()); out.put("title", mr.getTitle());
        out.put("status", mr.getStatus()); out.put("deliveredAt", mr.getDeliveredAt());
        out.put("deliveredBy", mr.getDeliveredBy()); out.put("createdAt", mr.getCreatedAt()); out.put("updatedAt", mr.getUpdatedAt());
        if (mr.getBriefId() != null) {
            out.put("boardCard", Map.of("submissionId", mr.getBriefId()));
        }
        Object payload = null;
        if (mr.getDeliveryPayload() != null && !mr.getDeliveryPayload().isBlank()) {
            try {
                payload = mapper.readTree(mr.getDeliveryPayload());
            } catch (JacksonException exception) {
                throw new InvalidOperationsRequestException(
                        "OPERATIONS_INVALID", "Could not read media delivery data.", exception);
            }
        }
        out.put("deliveryPayload", payload);
        return out;
    }

    private void notifySubmitter(String submitterId, MediaRequest mr, String note) {
        if (submitterId == null || !submitterId.contains("@")) return;
        notifications.publish(new NotificationEvent(NotificationType.MEDIA_DELIVERED,
                Set.of(submitterId), "Media delivery: " + mr.getCode(),
                note == null || note.isBlank() ? "Your media request has been delivered." : note,
                "media_request", mr.getId()));
    }

    private static boolean sameIdentity(String first, String second) {
        return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
    }
}
