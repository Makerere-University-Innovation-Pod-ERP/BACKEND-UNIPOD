package org.unipod.unipoderpbackend.service.operations;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.finance.CreateInvoiceRequest;
import org.unipod.unipoderpbackend.dto.operations.*;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.exception.operations.OperationsConflictException;
import org.unipod.unipoderpbackend.exception.operations.OperationsNotFoundException;
import org.unipod.unipoderpbackend.model.finance.InvoiceSourceType;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.DepartmentOrder;
import org.unipod.unipoderpbackend.model.operations.StockItem;
import org.unipod.unipoderpbackend.model.operations.StockMovement;
import org.unipod.unipoderpbackend.model.stock.StockMovementDraft;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;
import org.unipod.unipoderpbackend.repository.finance.InvoiceRepository;
import org.unipod.unipoderpbackend.repository.operations.DepartmentOrderRepository;
import org.unipod.unipoderpbackend.repository.operations.StockItemRepository;
import org.unipod.unipoderpbackend.repository.operations.StockMovementRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.finance.InvoiceService;

@Service
public class DepartmentOrderService {
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");

    private final DepartmentOrderRepository orders;
    private final InvoiceService invoices;
    private final InvoiceRepository invoiceRepo;
    private final StockItemRepository stockItems;
    private final StockMovementRepository stockMovements;
    private final AuditTrail auditTrail;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DepartmentOrderService(DepartmentOrderRepository orders, InvoiceService invoices, InvoiceRepository invoiceRepo,
                                  StockItemRepository stockItems, StockMovementRepository stockMovements,
                                  AuditTrail auditTrail, ObjectMapper mapper, Clock clock) {
        this.orders = orders; this.invoices = invoices; this.invoiceRepo = invoiceRepo;
        this.stockItems = stockItems; this.stockMovements = stockMovements;
        this.auditTrail = auditTrail; this.mapper = mapper; this.clock = clock;
    }

    @Transactional
    public Map<String, Object> create(String dept, DepartmentOrderRequest request, String actor) {
        DepartmentCode department = DepartmentCode.fromPath(dept);
        if (request.specs() == null || request.specs().isNull()) {
            throw new InvalidOperationsRequestException("specs is required and cannot be null.");
        }
        Instant now = clock.instant();
        int year = LocalDate.ofInstant(now, KAMPALA).getYear();
        long seq = orders.nextCodeSeq();
        String code = "ORD-" + department.name() + "-" + year + "-" + String.format("%06d", seq);
        String stage = "NEW";
        String specs = writeJson(request.specs());
        ArrayNode history = mapper.createArrayNode();
        history.add(historyEntry(stage, actor, "Order created", now, null));
        DepartmentOrder order = new DepartmentOrder(UUID.randomUUID(), code, department, actor,
                request.title().trim(), request.quantity() == null ? 1 : request.quantity(),
                request.date_needed(), clean(request.accepted_rules_version()), stage, specs,
                writeJson(history), actor, now);
        orders.saveAndFlush(order);
        auditTrail.record(actor, "order.created", "department_order", order.getId(), null,
                AuditTrail.details("code", code, "department", department.name(), "stage", stage));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listKanban(String dept, Pageable pageable) {
        DepartmentCode department = DepartmentCode.fromPath(dept);
        Page<DepartmentOrder> page = orders.findAllByDepartmentOrderByUpdatedAtDescCreatedAtDesc(department, pageable);
        Map<String, List<Map<String, Object>>> columns = new LinkedHashMap<>();
        for (String s : stagesFor(department)) columns.put(s, new ArrayList<>());
        for (DepartmentOrder o : page.getContent()) {
            columns.computeIfAbsent(o.getStage(), k -> new ArrayList<>()).add(toResponse(o));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("department", department.name());
        out.put("columns", columns);
        out.put("page", page.getNumber());
        out.put("size", page.getSize());
        out.put("totalElements", page.getTotalElements());
        out.put("totalPages", page.getTotalPages());
        return out;
    }

    @Transactional
    public Map<String, Object> quote(String dept, UUID id, DepartmentOrderQuoteRequest request, String actor) {
        DepartmentCode department = DepartmentCode.fromPath(dept);
        DepartmentOrder order = orders.findByIdAndDepartmentForUpdate(id, department)
                .orElseThrow(() -> new OperationsNotFoundException("Order not found."));
        if (request.quote() == null || request.quote().isNull()) {
            throw new InvalidOperationsRequestException("quote is required and cannot be null.");
        }
        JsonNode q = request.quote();
        long amount = q.path("amount").asLong(0);
        if (amount <= 0) throw new InvalidOperationsRequestException("quote.amount must be > 0.");
        String description = textOr(q, "description", "Department order " + order.getCode());
        String customerEmail = textOr(q, "customerEmail", order.getCustomerId());
        String customerName = textOr(q, "customerName", null);
        CreateInvoiceRequest invReq = new CreateInvoiceRequest(customerEmail, customerName, department.name(),
                description, amount, InvoiceSourceType.JOB, order.getId().toString());
        var invResp = invoices.create(invReq, actor);
        Instant now = clock.instant();
        order.applyQuote(writeJson(q), actor, invResp.id(), invResp.status().name(), now);
        String hist = appendHistoryJson(order.getStageHistory(), order.getStage(), actor, "Quote submitted, payment gate locked", now,
                Map.of("invoiceId", invResp.id().toString()));
        order.moveStage(order.getStage(), hist, now);
        orders.saveAndFlush(order);
        auditTrail.record(actor, "order.quoted", "department_order", order.getId(), null,
                AuditTrail.details("invoiceId", invResp.id().toString(), "amount", amount));
        return toResponse(order);
    }

    @Transactional
    public Map<String, Object> changeStage(String dept, UUID id, DepartmentOrderStageRequest request, String actor) {
        DepartmentCode department = DepartmentCode.fromPath(dept);
        DepartmentOrder order = orders.findByIdAndDepartmentForUpdate(id, department)
                .orElseThrow(() -> new OperationsNotFoundException("Order not found."));
        String target = normalizeStage(request.stage());
        String previous = order.getStage();
        Instant now = clock.instant();
        syncInvoiceStatus(order, now);
        validateStage(department, previous, target, order);
        if (isScheduledOrBeyond(target)
                && (order.getInvoiceId() == null || !isPaid(order.getInvoiceStatus()))) {
            throw new OperationsConflictException("PAYMENT_REQUIRED", "Payment is required before moving to " + target + ".");
        }
        String history = appendHistoryJson(order.getStageHistory(), target, actor, clean(request.notes()), now, null);
        order.moveStage(target, history, now);
        if (isCompletionStage(target)) {
            String materialsJson = request.materials_used() == null ? null : writeJson(request.materials_used());
            order.setCompletion(materialsJson, clean(request.machine_time_used()),
                    request.quality_checklist() == null ? null : writeJson(request.quality_checklist()),
                    clean(request.collection_signature()), now);
            // Re-submitting a completed order must not take its materials out of stock a second time.
            if (previous == null || !isCompletionStage(previous)) {
                deductStockIfAny(order, request.materials_used(), actor, now);
            }
            if (isPaid(order.getInvoiceStatus())) {
                order.confirmPayment(order.getInvoiceStatus(), now);
            }
        }
        orders.saveAndFlush(order);
        auditTrail.record(actor, "order.stage_changed", "department_order", order.getId(),
                AuditTrail.details("previous", previous), AuditTrail.details("stage", target));
        return toResponse(order);
    }

    private void validateStage(DepartmentCode department, String previous, String target, DepartmentOrder order) {
        List<String> allowed = stagesFor(department);
        if (!allowed.contains(target)) {
            throw new InvalidOperationsRequestException("Invalid stage '" + target + "' for department " + department.name() + ".");
        }
        if (Objects.equals(previous, target)) return;
        int prevIdx = allowed.indexOf(previous == null ? "NEW" : previous);
        int tgtIdx = allowed.indexOf(target);
        if (prevIdx >= 0 && tgtIdx >= 0 && tgtIdx < prevIdx && !isCompletionStage(target)) {
            throw new OperationsConflictException("INVALID_STAGE_TRANSITION", "Cannot move order backwards from " + previous + " to " + target + ".");
        }
        if ("SCHEDULED".equals(target) && order.getQuote() == null) {
            throw new InvalidOperationsRequestException("A quote must be submitted before scheduling.");
        }
    }

    private void syncInvoiceStatus(DepartmentOrder order, Instant now) {
        if (order.getInvoiceId() == null) return;
        invoiceRepo.findById(order.getInvoiceId()).ifPresent(inv -> {
            if (!inv.getStatus().name().equals(order.getInvoiceStatus())) {
                order.confirmPayment(inv.getStatus().name(), now);
            }
        });
    }

    private void deductStockIfAny(DepartmentOrder order, JsonNode materials, String actor, Instant now) {
        if (materials == null || materials.isNull()) return;
        if (materials.isArray()) {
            for (JsonNode n : materials) deductOne(order, n, actor, now);
        } else {
            deductOne(order, materials, actor, now);
        }
    }

    private void deductOne(DepartmentOrder order, JsonNode n, String actor, Instant now) {
        String sku = textOr(n, "sku", null);
        String itemId = textOr(n, "itemId", null);
        BigDecimal needed = quantityOf(n);
        if ((sku == null && itemId == null) || needed.signum() <= 0) return;

        StockItem item = null;
        if (itemId != null) {
            try { item = stockItems.findByIdForUpdate(UUID.fromString(itemId)).orElse(null); } catch (Exception ignored) {}
        }
        if (item == null && sku != null) {
            item = stockItems.findBySkuForUpdate(sku).orElse(null);
        }
        if (item == null) {
            throw new InvalidOperationsRequestException("Stock item not found for deduction: " + (sku != null ? sku : itemId));
        }
        if (item.getQuantityOnHand().compareTo(needed) < 0) {
            throw new OperationsConflictException("STOCK_INSUFFICIENT", "Insufficient stock for " + item.getSku());
        }
        StockMovement movement = item.record(new StockMovementDraft(StockMovementType.USED_ON_ORDER, needed,
                "Auto-deducted for order " + order.getCode(), order.getId().toString(), order.getCode(), order.getId()),
                actor, now);
        stockItems.save(item);
        stockMovements.save(movement);
    }

    /** `qty` or `quantity`, as a decimal: stock is counted in fractions too (1.5 metres), so nothing is rounded down. */
    private static BigDecimal quantityOf(JsonNode material) {
        JsonNode value = material.has("qty") ? material.get("qty") : material.path("quantity");
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText("0").trim());
        } catch (NumberFormatException notANumber) {
            return BigDecimal.ZERO;
        }
    }

    private String appendHistoryJson(String existing, String stage, String actor, String notes, Instant now, Map<String,String> extra) {
        ArrayNode arr = parseHistory(existing);
        arr.add(historyEntry(stage, actor, notes, now, extra));
        return writeJson(arr);
    }

    private ArrayNode parseHistory(String json) {
        try {
            if (json == null || json.isBlank()) return mapper.createArrayNode();
            JsonNode n = mapper.readTree(json);
            return n instanceof ArrayNode a ? a : mapper.createArrayNode();
        } catch (Exception e) {
            return mapper.createArrayNode();
        }
    }

    private ObjectNode historyEntry(String stage, String actor, String notes, Instant now, Map<String,String> extra) {
        ObjectNode n = mapper.createObjectNode();
        n.put("stage", stage); n.put("at", now.toString()); n.put("by", actor == null ? "" : actor);
        if (notes != null) n.put("notes", notes);
        if (extra != null) extra.forEach(n::put);
        return n;
    }

    private boolean isPaid(String status) { return "PAID".equals(status) || "RECEIPTED".equals(status); }
    private boolean isScheduledOrBeyond(String stage) {
        return Set.of("SCHEDULED","PRINTING","FINISHING","IN_PRODUCTION","READY","DONE","COLLECTED","DELIVERED","COMPLETED","CUTTING","SEWING","QC","IN_PROGRESS").contains(stage);
    }
    private boolean isCompletionStage(String stage) {
        return Set.of("READY","DONE","COLLECTED","DELIVERED","COMPLETED").contains(stage);
    }

    private List<String> stagesFor(DepartmentCode dept) {
        return switch (dept) {
            case MULTIMEDIA -> List.of("NEW","QUOTED","PAYMENT_GATE","SCHEDULED","IN_PRODUCTION","READY","DELIVERED","COMPLETED");
            case TEXTILE -> List.of("NEW","QUOTED","PAYMENT_GATE","SCHEDULED","CUTTING","SEWING","QC","READY","COLLECTED");
            case CNC -> List.of("NEW","QUOTED","PAYMENT_GATE","SCHEDULED","IN_PRODUCTION","READY","COLLECTED");
            case TELECOMS -> List.of("NEW","QUOTED","PAYMENT_GATE","SCHEDULED","IN_PROGRESS","READY","DONE");
        };
    }
    private String normalizeStage(String s) {
        if (s == null || s.isBlank()) throw new InvalidOperationsRequestException("stage is required.");
        return s.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    }

    private Map<String, Object> toResponse(DepartmentOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId()); m.put("code", o.getCode()); m.put("department", o.getDepartment().name());
        m.put("customerId", o.getCustomerId()); m.put("title", o.getTitle()); m.put("quantity", o.getQuantity());
        m.put("dateNeeded", o.getDateNeeded()); m.put("acceptedRulesVersion", o.getAcceptedRulesVersion());
        m.put("stage", o.getStage()); m.put("specs", readJson(o.getSpecs()));
        m.put("quote", readJson(o.getQuote())); m.put("quoteSubmittedAt", o.getQuoteSubmittedAt());
        m.put("quotedBy", o.getQuotedBy()); m.put("invoiceId", o.getInvoiceId());
        m.put("invoiceStatus", o.getInvoiceStatus()); m.put("materialsUsed", readJson(o.getMaterialsUsed()));
        m.put("paymentConfirmedAt", o.getPaymentConfirmedAt());
        m.put("machineTimeUsed", o.getMachineTimeUsed()); m.put("qualityChecklist", readJson(o.getQualityChecklist()));
        m.put("collectionSignature", o.getCollectionSignature()); m.put("stageHistory", readJson(o.getStageHistory()));
        m.put("createdBy", o.getCreatedBy()); m.put("createdAt", o.getCreatedAt()); m.put("updatedAt", o.getUpdatedAt());
        return m;
    }

    private String writeJson(Object n) {
        if (n == null) return null;
        try { return n instanceof String s ? s : mapper.writeValueAsString(n); }
        catch (Exception e) { return "{}"; }
    }
    private Object readJson(String s) {
        if (s == null || s.isBlank()) return null;
        try { return mapper.readTree(s); } catch (Exception e) { return s; }
    }
    private static String clean(String v) { if (v == null) return null; String t = v.trim(); return t.isEmpty()?null:t; }
    private String textOr(JsonNode n, String field, String def) { if (n == null) return def; JsonNode v = n.get(field); return v == null || v.isNull() || v.asText().isBlank() ? def : v.asText(); }
}
