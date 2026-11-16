package org.unipod.unipoderpbackend.service.operations;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.finance.CreateInvoiceRequest;
import org.unipod.unipoderpbackend.dto.operations.MediaContractRequest;
import org.unipod.unipoderpbackend.dto.operations.MediaContractStatusRequest;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.exception.operations.OperationsNotFoundException;
import org.unipod.unipoderpbackend.model.finance.InvoiceSourceType;
import org.unipod.unipoderpbackend.model.operations.MediaContract;
import org.unipod.unipoderpbackend.model.operations.MediaContractStatus;
import org.unipod.unipoderpbackend.repository.operations.MediaContractRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.finance.InvoiceService;

@Service
public class MediaContractService {
    private final MediaContractRepository contracts;
    private final SubmissionDocumentRepository documents;
    private final InvoiceService invoices;
    private final AuditTrail auditTrail;
    private final ObjectMapper mapper;
    private final Clock clock;
    public MediaContractService(MediaContractRepository contracts, SubmissionDocumentRepository documents,
                                InvoiceService invoices, AuditTrail auditTrail, ObjectMapper mapper, Clock clock) {
        this.contracts = contracts; this.documents = documents; this.invoices = invoices;
        this.auditTrail = auditTrail; this.mapper = mapper; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return contracts.findAllByOrderByEventDateDescCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public Map<String, Object> create(MediaContractRequest request, String actor) {
        if (request.signed_contract_doc_id() != null
                && !documents.existsById(request.signed_contract_doc_id())) {
            throw new OperationsNotFoundException("Signed contract document not found.");
        }
        Instant now = clock.instant();
        long seq = contracts.nextCodeSeq();
        int year = LocalDate.ofInstant(now, java.time.ZoneId.of("Africa/Kampala")).getYear();
        String code = "MCTR-" + year + "-" + String.format("%04d", seq);
        String milestones = request.milestone_payment_dates() == null ? "[]" : writeJson(request.milestone_payment_dates());
        String staff = request.assigned_staff() == null ? "[]" : writeJson(request.assigned_staff());
        MediaContract c = new MediaContract(UUID.randomUUID(), code, request.client().trim(), request.event_name().trim(),
                request.event_date(), clean(request.venue()), request.agreed_price(),
                request.currency(), milestones,
                request.signed_contract_doc_id(), staff, actor, now);
        contracts.saveAndFlush(c);
        if (Boolean.TRUE.equals(request.generate_invoices())) {
            if (!"UGX".equalsIgnoreCase(c.getCurrency())) {
                throw new InvalidOperationsRequestException("Invoices can only be generated for UGX media contracts.");
            }
            if (request.customer_email() == null || request.customer_email().isBlank()) {
                throw new InvalidOperationsRequestException("customer_email is required when generating invoices.");
            }
            generateMilestoneInvoices(c, request, actor);
            contracts.saveAndFlush(c);
        }
        auditTrail.record(actor, "media_contract.created", "media_contract", c.getId(), null,
                AuditTrail.details("code", code, "client", c.getClient()));
        return toResponse(c);
    }

    @Transactional
    public Map<String, Object> transition(UUID id, MediaContractStatusRequest request, String actor) {
        MediaContract contract = contracts.findById(id)
                .orElseThrow(() -> new OperationsNotFoundException("Media contract not found."));
        if (request.signedContractDocId() != null
                && !documents.existsById(request.signedContractDocId())) {
            throw new OperationsNotFoundException("Signed contract document not found.");
        }
        String previous = contract.getStatus();
        Instant now = clock.instant();
        contract.transitionTo(request.status(), request.signedContractDocId(), now);
        contracts.saveAndFlush(contract);
        auditTrail.record(actor, "media_contract.status_changed", "media_contract", contract.getId(),
                AuditTrail.details("status", previous),
                AuditTrail.details("status", contract.getStatus()));
        return toResponse(contract);
    }

    private void generateMilestoneInvoices(MediaContract c, MediaContractRequest request, String actor) {
        String customerEmail = request.customer_email().trim();
        String customerName = request.client().trim();
        JsonNode node;
        try {
            node = mapper.readTree(c.getMilestonePaymentDates());
        } catch (JacksonException exception) {
            throw new InvalidOperationsRequestException(
                    "OPERATIONS_INVALID", "Could not read milestone payment dates.", exception);
        }
        List<UUID> ids = new ArrayList<>();
        if (node.isArray() && node.size() > 0) {
            long share = Math.max(1, c.getAgreedPrice() / node.size());
            int idx = 0;
            for (JsonNode m : node) {
                idx++;
                String label = m.isObject() && m.hasNonNull("label") ? m.get("label").asText() : ("Milestone " + idx);
                long amount = m.isObject() && m.has("amount") ? m.get("amount").asLong(share) : share;
                var inv = invoices.create(new CreateInvoiceRequest(customerEmail, customerName, "MULTIMEDIA",
                        "Media contract " + c.getCode() + " - " + label, amount, InvoiceSourceType.JOB, c.getId().toString()), actor);
                ids.add(inv.id());
            }
        } else {
            var inv = invoices.create(new CreateInvoiceRequest(customerEmail, customerName, "MULTIMEDIA",
                    "Media contract " + c.getCode() + " - " + c.getEventName(), c.getAgreedPrice(), InvoiceSourceType.JOB, c.getId().toString()), actor);
            ids.add(inv.id());
        }
        c.setInvoiceIds(writeJson(ids), clock.instant());
    }

    private Map<String, Object> toResponse(MediaContract c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId()); m.put("code", c.getCode()); m.put("client", c.getClient());
        m.put("eventName", c.getEventName()); m.put("eventDate", c.getEventDate()); m.put("venue", c.getVenue());
        m.put("agreedPrice", c.getAgreedPrice()); m.put("currency", c.getCurrency());
        m.put("milestonePaymentDates", readJson(c.getMilestonePaymentDates()));
        m.put("signedContractDocId", c.getSignedContractDocId()); m.put("assignedStaff", readJson(c.getAssignedStaff()));
        m.put("status", c.getStatus()); m.put("invoiceIds", readJson(c.getInvoiceIds()));
        m.put("createdBy", c.getCreatedBy()); m.put("createdAt", c.getCreatedAt()); m.put("updatedAt", c.getUpdatedAt());
        return m;
    }
    private String writeJson(Object n) {
        try { return n instanceof String s ? s : mapper.writeValueAsString(n); }
        catch (JacksonException exception) {
            throw new InvalidOperationsRequestException(
                    "OPERATIONS_INVALID", "Could not serialize media contract data.", exception);
        }
    }
    private Object readJson(String s) { if (s==null||s.isBlank()) return List.of(); try { return mapper.readTree(s); } catch (Exception e) { return s; } }
    private static String clean(String v){ if(v==null) return null; String t=v.trim(); return t.isEmpty()?null:t; }
}
