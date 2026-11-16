package org.unipod.unipoderpbackend.service.operations;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.finance.CreateInvoiceRequest;
import org.unipod.unipoderpbackend.dto.operations.MediaContractRequest;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.model.finance.InvoiceSourceType;
import org.unipod.unipoderpbackend.model.operations.MediaContract;
import org.unipod.unipoderpbackend.repository.operations.MediaContractRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.finance.InvoiceService;

@Service
public class MediaContractService {
    private final MediaContractRepository contracts;
    private final InvoiceService invoices;
    private final AuditTrail auditTrail;
    private final ObjectMapper mapper;
    private final Clock clock;
    public MediaContractService(MediaContractRepository contracts, InvoiceService invoices, AuditTrail auditTrail, ObjectMapper mapper, Clock clock) {
        this.contracts = contracts; this.invoices = invoices; this.auditTrail = auditTrail; this.mapper = mapper; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        return contracts.findAllByOrderByEventDateDescCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public Map<String, Object> create(MediaContractRequest request, String actor) {
        Instant now = clock.instant();
        long seq = contracts.nextCodeSeq();
        int year = LocalDate.ofInstant(now, java.time.ZoneId.of("Africa/Kampala")).getYear();
        String code = "MCTR-" + year + "-" + String.format("%04d", seq);
        String milestones = request.milestone_payment_dates() == null ? "[]" : writeJson(request.milestone_payment_dates());
        String staff = request.assigned_staff() == null ? "[]" : writeJson(request.assigned_staff());
        MediaContract c = new MediaContract(UUID.randomUUID(), code, request.client().trim(), request.event_name().trim(),
                request.event_date(), clean(request.venue()), request.agreed_price(),
                request.currency() == null ? "UGX" : request.currency(), milestones,
                request.signed_contract_doc_id(), staff, actor, now);
        contracts.saveAndFlush(c);
        if (Boolean.TRUE.equals(request.generate_invoices())) {
            generateMilestoneInvoices(c, request, actor);
            contracts.saveAndFlush(c);
        }
        auditTrail.record(actor, "media_contract.created", "media_contract", c.getId(), null,
                AuditTrail.details("code", code, "client", c.getClient()));
        return toResponse(c);
    }

    private void generateMilestoneInvoices(MediaContract c, MediaContractRequest request, String actor) {
        String customerEmail = clean(request.client());
        String customerName = clean(request.client());
        try {
            JsonNode node = mapper.readTree(c.getMilestonePaymentDates());
            List<UUID> ids = new ArrayList<>();
            if (node.isArray() && node.size() > 0) {
                long share = Math.max(1, c.getAgreedPrice() / node.size());
                int idx = 0;
                for (JsonNode m : node) {
                    idx++;
                    String label = m.isObject() && m.hasNonNull("label") ? m.get("label").asText() : ("Milestone " + idx);
                    long amount = m.isObject() && m.has("amount") ? m.get("amount").asLong(share) : share;
                    String email = (m.isObject() && m.hasNonNull("customerEmail")) ? m.get("customerEmail").asText(customerEmail) : customerEmail;
                    String name = (m.isObject() && m.hasNonNull("customerName")) ? m.get("customerName").asText(customerName) : customerName;
                    var inv = invoices.create(new CreateInvoiceRequest(email, name, "MULTIMEDIA",
                            "Media contract " + c.getCode() + " - " + label, amount, InvoiceSourceType.JOB, c.getId().toString()), actor);
                    ids.add(inv.id());
                }
            } else {
                var inv = invoices.create(new CreateInvoiceRequest(customerEmail, customerName, "MULTIMEDIA",
                        "Media contract " + c.getCode() + " - " + c.getEventName(), c.getAgreedPrice(), InvoiceSourceType.JOB, c.getId().toString()), actor);
                ids.add(inv.id());
            }
            c.setInvoiceIds(writeJson(ids), clock.instant());
        } catch (Exception e) {
            throw new InvalidOperationsRequestException("Could not generate milestone invoices: " + e.getMessage());
        }
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
    private String writeJson(Object n) { try { return n instanceof String s ? s : mapper.writeValueAsString(n); } catch (Exception e) { return "[]"; } }
    private Object readJson(String s) { if (s==null||s.isBlank()) return List.of(); try { return mapper.readTree(s); } catch (Exception e) { return s; } }
    private static String clean(String v){ if(v==null) return null; String t=v.trim(); return t.isEmpty()?null:t; }
}
