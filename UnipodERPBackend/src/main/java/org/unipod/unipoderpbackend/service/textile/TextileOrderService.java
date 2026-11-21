package org.unipod.unipoderpbackend.service.textile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.dto.textile.TextileMaterialLine;
import org.unipod.unipoderpbackend.dto.textile.TextileMessageRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileOrderRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileProductionRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileQualityRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileQuoteRequest;
import org.unipod.unipoderpbackend.dto.textile.TextileTransitionRequest;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.exception.operations.OperationsConflictException;
import org.unipod.unipoderpbackend.exception.operations.OperationsNotFoundException;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.equipment.Machine;
import org.unipod.unipoderpbackend.model.equipment.MachineState;
import org.unipod.unipoderpbackend.model.finance.InvoiceSourceType;
import org.unipod.unipoderpbackend.model.finance.InvoiceStatus;
import org.unipod.unipoderpbackend.model.finance.PriceItemStatus;
import org.unipod.unipoderpbackend.model.finance.PriceListItem;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.DepartmentOrder;
import org.unipod.unipoderpbackend.model.operations.StockItem;
import org.unipod.unipoderpbackend.repository.equipment.MachineRepository;
import org.unipod.unipoderpbackend.repository.finance.InvoiceRepository;
import org.unipod.unipoderpbackend.repository.finance.PriceListItemRepository;
import org.unipod.unipoderpbackend.repository.operations.DepartmentOrderRepository;
import org.unipod.unipoderpbackend.repository.operations.StockItemRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.finance.PriceListService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;
import org.unipod.unipoderpbackend.service.sector.SectorService;

@Service
public class TextileOrderService {
    private static final DepartmentCode DEPARTMENT = DepartmentCode.TEXTILE;
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");
    private static final long MAX_PHOTO_BYTES = 15L * 1024 * 1024;

    private final DepartmentOrderRepository orders;
    private final StockItemRepository stockItems;
    private final MachineRepository machines;
    private final PriceListItemRepository priceItems;
    private final InvoiceRepository invoices;
    private final PriceListService priceList;
    private final SectorService sectors;
    private final DepartmentAccess access;
    private final AuditTrail audit;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TextileOrderService(DepartmentOrderRepository orders, StockItemRepository stockItems,
                               MachineRepository machines, PriceListItemRepository priceItems,
                               InvoiceRepository invoices, PriceListService priceList, SectorService sectors,
                               DepartmentAccess access, AuditTrail audit, ObjectMapper mapper, Clock clock) {
        this.orders = orders;
        this.stockItems = stockItems;
        this.machines = machines;
        this.priceItems = priceItems;
        this.invoices = invoices;
        this.priceList = priceList;
        this.sectors = sectors;
        this.access = access;
        this.audit = audit;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list() {
        List<Map<String, Object>> items = orders.findAllByDepartmentOrderByUpdatedAtDescCreatedAtDesc(
                        DEPARTMENT, PageRequest.of(0, 500)).getContent().stream()
                .map(this::toResponse).toList();
        return Map.of("items", items);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public Map<String, Object> create(TextileOrderRequest request, MultipartFile photo, Actor actor) {
        validateSubmission(request);
        validatePhoto(photo);
        Instant now = clock.instant();
        String specs = buildSpecs(request, photoName(photo), null);
        String stage = request.submit() ? "NEW" : "DRAFT";
        String code = request.submit() ? nextCode(now) : null;
        ArrayNode history = mapper.createArrayNode();
        history.add(historyEntry(stage, actor.email(), request.submit() ? "Order sent to Textile" : "Draft saved", now));
        DepartmentOrder order = new DepartmentOrder(UUID.randomUUID(), code == null ? "DRF-" + UUID.randomUUID() : code,
                DEPARTMENT, actor.email(), clean(request.whatIsWanted()), 1,
                request.dateNeeded(), "textile-rules-v1", stage, specs,
                writeJson(history), actor.email(), now);
        order.setCompletion(null, null, writeJson(defaultQuality()), null, now);
        if (request.quantity() != null && !request.quantity().isBlank()) {
            // Quantity is deliberately kept as the customer's text in specs, matching the screen.
        }
        orders.saveAndFlush(order);
        audit.record(actor.email(), "textile_order.created", "textile_order", order.getId(), null,
                AuditTrail.details("code", code, "status", stage));
        return toResponse(order);
    }

    @Transactional
    public Map<String, Object> update(UUID id, TextileOrderRequest request, MultipartFile photo, Actor actor) {
        DepartmentOrder order = requireForUpdate(id);
        if (!"DRAFT".equals(order.getStage())) {
            throw new OperationsConflictException("Only a draft order can be edited.");
        }
        validateSubmission(request);
        validatePhoto(photo);
        JsonNode oldSpecs = readJson(order.getSpecs());
        String oldPhoto = oldSpecs.path("photo").path("name").asText("");
        String photoName = photoName(photo);
        if (photoName == null) photoName = oldPhoto.isBlank() ? null : oldPhoto;
        JsonNode existingEstimate = readJson(order.getSpecs()).path("estimate");
        String specs = buildSpecs(request, photoName, existingEstimate);
        Instant now = clock.instant();
        order.moveStage(request.submit() ? "NEW" : "DRAFT",
                appendHistory(order.getStageHistory(), request.submit() ? "NEW" : "DRAFT",
                        actor.email(), request.submit() ? "Order sent to Textile" : "Draft updated", now),
                now);
        order.applySpecs(specs, now);
        if (request.submit()) {
            String code = nextCode(now);
            order.applyCode(code);
        }
        orders.saveAndFlush(order);
        audit.record(actor.email(), "textile_order.updated", "textile_order", order.getId(), null,
                AuditTrail.details("status", order.getStage()));
        return toResponse(order);
    }

    @Transactional
    public List<Map<String, Object>> priceList() {
        UUID departmentId = sectors.activeSectorByName("Textile")
                .orElseThrow(() -> new InvalidOperationsRequestException("The Textile department is not set up."))
                .getId();
        return priceItems.search(departmentId, PriceItemStatus.ACTIVE).stream()
                .map(item -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("id", item.getId());
                    out.put("label", item.getService());
                    out.put("unit", item.getUnit().name());
                    out.put("unitPrice", item.getPriceUgx());
                    out.put("leadDays", 7);
                    return out;
                }).toList();
    }

    @Transactional
    public Map<String, Object> quote(UUID id, TextileQuoteRequest request, Actor actor) {
        access.requireManage(actor, DEPARTMENT);
        DepartmentOrder order = requireForUpdate(id);
        if (!"NEW".equals(order.getStage()) && !"AWAITING_DETAILS".equals(order.getStage())) {
            throw new OperationsConflictException("A quote cannot be sent for an order in " + order.getStage() + " status.");
        }
        if (request.materials() == null || request.materials().isEmpty()) {
            throw new InvalidOperationsRequestException("Add at least one estimated material.");
        }
        if (request.expectedCompletion().isBefore(LocalDate.now(KAMPALA))) {
            throw new InvalidOperationsRequestException("The expected completion date cannot be in the past.");
        }
        PriceListItem price = priceList.requireActive(request.priceListItemId());
        for (TextileMaterialLine line : request.materials()) {
            StockItem item = stockItems.findByIdForUpdate(line.itemId())
                    .filter(stock -> "TEXTILE".equals(stock.getDepartment()))
                    .orElseThrow(() -> new InvalidOperationsRequestException("Stock item not found."));
            if (item.getQuantityOnHand().compareTo(line.quantity()) < 0) {
                throw new OperationsConflictException("Not enough " + item.getName() + " to take this order.");
            }
        }
        long amount = Math.multiplyExact(price.getPriceUgx(), request.priceQuantity().intValueExact());
        Instant now = clock.instant();
        var invoice = priceList.invoiceFor(price.getId(), actor.email(), customerName(order),
                request.priceQuantity().intValueExact(), InvoiceSourceType.JOB, order.getId().toString(), actor.email());
        ObjectNode estimate = mapper.createObjectNode();
        ArrayNode materials = estimate.putArray("materials");
        for (TextileMaterialLine line : request.materials()) {
            ObjectNode material = materials.addObject();
            material.put("itemId", line.itemId().toString());
            material.put("quantity", line.quantity());
        }
        estimate.put("priceListItemId", price.getId().toString());
        estimate.put("priceQuantity", request.priceQuantity());
        ObjectNode quote = mapper.createObjectNode();
        quote.put("amount", amount);
        quote.put("currency", "UGX");
        quote.put("priceListItemId", price.getId().toString());
        quote.put("priceQuantity", request.priceQuantity());
        quote.put("expectedCompletion", request.expectedCompletion().toString());
        quote.put("sentAt", now.toString());
        quote.put("sentBy", actor.email());
        order.applyQuote(writeJson(quote), actor.email(), invoice.id(), invoice.status().name(), now);
        mergeSpecs(order, "estimate", estimate);
        move(order, "QUOTED", actor.email(), "Quote UGX " + amount + " sent", now);
        orders.saveAndFlush(order);
        audit.record(actor.email(), "textile_order.quoted", "textile_order", order.getId(), null,
                AuditTrail.details("invoiceId", invoice.id(), "amount", amount));
        return toResponse(order);
    }

    @Transactional
    public Map<String, Object> requestDetails(UUID id, String message, Actor actor) {
        access.requireManage(actor, DEPARTMENT);
        DepartmentOrder order = requireForUpdate(id);
        requireStage(order, "NEW", "AWAITING_DETAILS");
        move(order, "AWAITING_DETAILS", actor.email(), required(message, "Say what details the customer needs to provide."),
                clock.instant());
        orders.saveAndFlush(order);
        return toResponse(order);
    }

    @Transactional
    public Map<String, Object> sendBack(UUID id, String reason, Actor actor) {
        access.requireManage(actor, DEPARTMENT);
        DepartmentOrder order = requireForUpdate(id);
        requireStage(order, "NEW", "AWAITING_DETAILS");
        move(order, "RETURNED", actor.email(),
                required(reason, "Give reception a reason for sending the order back."), clock.instant());
        orders.saveAndFlush(order);
        return toResponse(order);
    }

    @Transactional
    public Map<String, Object> transition(UUID id, TextileTransitionRequest request, Actor actor) {
        access.requireManage(actor, DEPARTMENT);
        DepartmentOrder order = requireForUpdate(id);
        String target = normalize(request.toStatus());
        String previous = order.getStage();
        Instant now = clock.instant();
        syncInvoice(order);
        if ("SCHEDULED".equals(target)) {
            requireStage(order, "QUOTED");
            if (!isPaid(order.getInvoiceStatus())) {
                throw new OperationsConflictException("An order can only be scheduled after Finance confirms payment.");
            }
        } else if ("BEING_MADE".equals(target)) {
            requireStage(order, "SCHEDULED");
        } else if ("READY".equals(target)) {
            requireStage(order, "BEING_MADE");
            if (!qualityComplete(order)) {
                throw new OperationsConflictException("Complete every quality check before marking the order ready.");
            }
        } else {
            throw new InvalidOperationsRequestException("Invalid textile order stage transition.");
        }
        move(order, target, actor.email(), null, now);
        orders.saveAndFlush(order);
        audit.record(actor.email(), "textile_order.stage_changed", "textile_order", order.getId(),
                AuditTrail.details("previous", previous), AuditTrail.details("status", target));
        return toResponse(order);
    }

    @Transactional
    public Map<String, Object> saveProduction(UUID id, TextileProductionRequest request, Actor actor) {
        access.requireManage(actor, DEPARTMENT);
        DepartmentOrder order = requireForUpdate(id);
        if (!List.of("SCHEDULED", "BEING_MADE", "READY").contains(order.getStage())) {
            throw new OperationsConflictException("Production details can only be saved while an order is in production.");
        }
        if ("COLLECTED".equals(order.getStage())) {
            throw new OperationsConflictException("Collected orders cannot be edited.");
        }
        if (request.machineId() != null) {
            Machine machine = machines.findByIdForUpdate(request.machineId())
                    .filter(m -> m.getDepartment() == DEPARTMENT)
                    .orElseThrow(() -> new InvalidOperationsRequestException("Machine not found."));
            if (machine.getState() != MachineState.WORKING
                    && !machine.getId().equals(currentMachineId(order))) {
                throw new OperationsConflictException(machine.getName() + " cannot be used until it is marked working.");
            }
        }
        Instant now = clock.instant();
        ObjectNode production = mapper.createObjectNode();
        production.put("materialUsed", clean(request.materialUsed()));
        if (request.machineId() != null) production.put("machineId", request.machineId().toString());
        else production.putNull("machineId");
        production.put("staff", clean(request.staff()));
        production.put("notes", clean(request.notes()));
        mergeSpecs(order, "production", production);
        order.moveStage(order.getStage(), order.getStageHistory(), now);
        orders.saveAndFlush(order);
        return toResponse(order);
    }

    @Transactional
    public Map<String, Object> saveQuality(UUID id, TextileQualityRequest request, Actor actor) {
        access.requireManage(actor, DEPARTMENT);
        DepartmentOrder order = requireForUpdate(id);
        if (!"BEING_MADE".equals(order.getStage())) {
            throw new OperationsConflictException("The quality checklist can only be changed while making.");
        }
        JsonNode existing = readJson(order.getQualityChecklist());
        if (!existing.isArray()) existing = defaultQuality();
        Map<String, Boolean> incoming = new LinkedHashMap<>();
        if (request.checklist() != null) {
            request.checklist().forEach(item -> incoming.put(item.id().toString(), item.checked()));
        }
        ArrayNode checklist = mapper.createArrayNode();
        if (existing.isArray()) {
            for (JsonNode item : existing) {
                String key = item.path("id").asText();
                ObjectNode copy = checklist.addObject();
                copy.put("id", key);
                copy.put("label", item.path("label").asText());
                copy.put("checked", incoming.getOrDefault(key, item.path("checked").asBoolean(false)));
            }
        }
        order.setCompletion(order.getMaterialsUsed(), order.getMachineTimeUsed(), writeJson(checklist),
                order.getCollectionSignature(), clock.instant());
        orders.saveAndFlush(order);
        return toResponse(order);
    }

    @Transactional
    public Map<String, Object> recordCollection(UUID id, String collectedBy, Actor actor) {
        access.requireManage(actor, DEPARTMENT);
        DepartmentOrder order = requireForUpdate(id);
        requireStage(order, "READY");
        if (!qualityComplete(order)) {
            throw new OperationsConflictException("The quality checklist is not complete.");
        }
        String name = required(collectedBy, "Record who collected the order.");
        Instant now = clock.instant();
        ObjectNode collection = mapper.createObjectNode();
        collection.put("collectedBy", name);
        collection.put("collectedAt", now.toString());
        collection.put("recordedBy", actor.email());
        mergeSpecs(order, "collection", collection);
        move(order, "COLLECTED", actor.email(), "Collected by " + name, now);
        orders.saveAndFlush(order);
        audit.record(actor.email(), "textile_order.collected", "textile_order", order.getId(), null,
                AuditTrail.details("collectedBy", name));
        return toResponse(order);
    }

    private DepartmentOrder require(UUID id) {
        return orders.findByIdAndDepartment(id, DEPARTMENT)
                .orElseThrow(() -> new OperationsNotFoundException("Order not found."));
    }

    private DepartmentOrder requireForUpdate(UUID id) {
        return orders.findByIdAndDepartmentForUpdate(id, DEPARTMENT)
                .orElseThrow(() -> new OperationsNotFoundException("Order not found."));
    }

    private void validateSubmission(TextileOrderRequest request) {
        if (request.submit()) {
            for (String value : List.of(request.customerName(), request.customerType(),
                    request.whatIsWanted(), request.quantity(), request.sizeDesign())) {
                if (value == null || value.isBlank()) {
                    throw new InvalidOperationsRequestException("Fill in every required field before sending the order.");
                }
            }
            if (!request.rulesAccepted()) {
                throw new InvalidOperationsRequestException("The customer must accept the Textile department's rules.");
            }
        } else if (request.customerName() == null || request.customerName().isBlank()) {
            throw new InvalidOperationsRequestException("A draft needs at least the customer name.");
        }
    }

    private void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) return;
        String type = photo.getContentType();
        if (!List.of("image/jpeg", "image/png").contains(type)) {
            throw new InvalidOperationsRequestException("The photo must be a JPG or PNG image.");
        }
        if (photo.getSize() > MAX_PHOTO_BYTES) {
            throw new InvalidOperationsRequestException("The photo must be 15 MB or smaller.");
        }
    }

    private String photoName(MultipartFile photo) {
        return photo == null || photo.isEmpty() ? null : clean(photo.getOriginalFilename());
    }

    private String buildSpecs(TextileOrderRequest request, String photoName, JsonNode estimate) {
        ObjectNode specs = mapper.createObjectNode();
        specs.put("customerName", clean(request.customerName()));
        specs.put("customerType", clean(request.customerType()));
        specs.put("whatIsWanted", clean(request.whatIsWanted()));
        specs.put("quantity", clean(request.quantity()));
        specs.put("sizeDesign", clean(request.sizeDesign()));
        if (request.dateNeeded() != null) specs.put("dateNeeded", request.dateNeeded().toString());
        else specs.putNull("dateNeeded");
        specs.put("rulesAccepted", request.rulesAccepted());
        if (photoName == null) specs.putNull("photo");
        else {
            ObjectNode photo = specs.putObject("photo");
            photo.put("name", photoName);
            photo.putNull("url");
        }
        if (estimate != null && !estimate.isNull()) specs.set("estimate", estimate);
        return writeJson(specs);
    }

    private void mergeSpecs(DepartmentOrder order, String field, JsonNode value) {
        JsonNode existing = readJson(order.getSpecs());
        ObjectNode specs = existing instanceof ObjectNode object ? object.deepCopy() : mapper.createObjectNode();
        specs.set(field, value);
        order.applySpecs(writeJson(specs), clock.instant());
    }

    private String nextCode(Instant now) {
        int year = LocalDate.ofInstant(now, KAMPALA).getYear();
        long sequence = orders.nextTextileCodeSeq();
        return "TXO-" + year + "-" + String.format("%03d", sequence);
    }

    private void move(DepartmentOrder order, String stage, String actor, String note, Instant now) {
        order.moveStage(stage, appendHistory(order.getStageHistory(), stage, actor, note, now), now);
    }

    private String appendHistory(String existing, String stage, String actor, String note, Instant now) {
        ArrayNode history = parseHistory(existing);
        history.add(historyEntry(stage, actor, note, now));
        return writeJson(history);
    }

    private ObjectNode historyEntry(String stage, String actor, String note, Instant now) {
        ObjectNode entry = mapper.createObjectNode();
        entry.put("stage", stage);
        entry.put("by", actor);
        entry.put("at", now.toString());
        if (note != null) entry.put("notes", note);
        return entry;
    }

    private ArrayNode parseHistory(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return node instanceof ArrayNode array ? array : mapper.createArrayNode();
        } catch (Exception e) {
            return mapper.createArrayNode();
        }
    }

    private void syncInvoice(DepartmentOrder order) {
        if (order.getInvoiceId() == null) return;
        invoices.findById(order.getInvoiceId()).ifPresent(invoice ->
                order.confirmPayment(invoice.getStatus().name(), clock.instant()));
    }

    private boolean isPaid(String status) {
        return "PAID".equals(status) || "RECEIPTED".equals(status);
    }

    private boolean qualityComplete(DepartmentOrder order) {
        JsonNode checklist = readJson(order.getQualityChecklist());
        if (!checklist.isArray() || checklist.isEmpty()) return false;
        for (JsonNode item : checklist) if (!item.path("checked").asBoolean(false)) return false;
        return true;
    }

    private UUID currentMachineId(DepartmentOrder order) {
        JsonNode production = readJson(order.getSpecs()).path("production");
        String id = production.path("machineId").asText("");
        try { return id.isBlank() ? null : UUID.fromString(id); } catch (Exception e) { return null; }
    }

    private void requireStage(DepartmentOrder order, String... allowed) {
        for (String stage : allowed) if (stage.equals(order.getStage())) return;
        throw new OperationsConflictException("This action is not valid for an order in " + order.getStage() + " status.");
    }

    private String normalize(String status) {
        if (status == null) throw new InvalidOperationsRequestException("toStatus is required.");
        return status.trim().toUpperCase();
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw new InvalidOperationsRequestException(message);
        return value.trim();
    }

    private static String clean(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed;
    }

    private String customerName(DepartmentOrder order) {
        String name = readJson(order.getSpecs()).path("customerName").asText("");
        return name.isBlank() ? "Textile customer" : name;
    }

    private Map<String, Object> toResponse(DepartmentOrder order) {
        JsonNode specs = readJson(order.getSpecs());
        JsonNode estimate = specs.path("estimate");
        JsonNode quote = readJson(order.getQuote());
        JsonNode production = specs.path("production");
        JsonNode collection = specs.path("collection");
        String status = order.getStage();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", order.getId());
        out.put("code", "DRAFT".equals(status) ? null : order.getCode());
        out.put("customerName", specs.path("customerName").asText(""));
        out.put("customerType", specs.path("customerType").asText(""));
        out.put("whatIsWanted", specs.path("whatIsWanted").asText(""));
        out.put("quantity", specs.path("quantity").asText(""));
        out.put("sizeDesign", specs.path("sizeDesign").asText(""));
        out.put("dateNeeded", specs.path("dateNeeded").isMissingNode() || specs.path("dateNeeded").isNull()
                ? null : specs.path("dateNeeded").asText());
        if (specs.path("photo").isMissingNode() || specs.path("photo").isNull()) {
            out.put("photo", null);
        } else {
            Map<String, Object> photo = new LinkedHashMap<>();
            photo.put("name", specs.path("photo").path("name").asText(""));
            photo.put("url", specs.path("photo").path("url").isNull()
                    ? null : specs.path("photo").path("url").asText());
            out.put("photo", photo);
        }
        out.put("rulesAccepted", specs.path("rulesAccepted").asBoolean(false));
        out.put("status", status);
        out.put("createdAt", order.getCreatedAt());
        out.put("updatedAt", order.getUpdatedAt());
        out.put("estimate", estimate.isMissingNode() || estimate.isNull() ? null : readEstimate(estimate));
        out.put("quote", quote.isMissingNode() || quote.isNull() ? null : readQuote(quote));
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("status", paymentStatus(order));
        payment.put("reference", order.getInvoiceId() == null ? null : order.getInvoiceId().toString());
        payment.put("confirmedAt", order.getPaymentConfirmedAt() == null
                ? null : order.getPaymentConfirmedAt().toString());
        out.put("payment", payment);
        Map<String, Object> productionOut = new LinkedHashMap<>();
        productionOut.put("materialUsed", production.path("materialUsed").asText(""));
        productionOut.put("machineId", production.path("machineId").isMissingNode() || production.path("machineId").isNull()
                ? null : production.path("machineId").asText(""));
        productionOut.put("staff", production.path("staff").asText(""));
        productionOut.put("notes", production.path("notes").asText(""));
        out.put("production", productionOut);
        JsonNode quality = readJson(order.getQualityChecklist());
        if (!quality.isArray()) {
            quality = defaultQuality();
            order.setCompletion(order.getMaterialsUsed(), order.getMachineTimeUsed(), writeJson(quality),
                    order.getCollectionSignature(), order.getUpdatedAt());
        }
        out.put("qualityChecklist", quality);
        out.put("collection", collection.isMissingNode() || collection.isNull() ? null
                : Map.of("collectedBy", collection.path("collectedBy").asText(),
                        "collectedAt", collection.path("collectedAt").asText(),
                        "recordedBy", collection.path("recordedBy").asText()));
        out.put("history", readHistory(order.getStageHistory()));
        return out;
    }

    private Map<String, Object> readEstimate(JsonNode estimate) {
        List<Map<String, Object>> materials = new ArrayList<>();
        if (estimate.path("materials").isArray()) {
            for (JsonNode line : estimate.path("materials")) {
                Map<String, Object> material = new LinkedHashMap<>();
                material.put("itemId", line.path("itemId").asText(""));
                material.put("quantity", line.path("quantity").decimalValue());
                materials.add(material);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("materials", materials);
        out.put("priceListItemId", estimate.path("priceListItemId").isNull()
                ? null : estimate.path("priceListItemId").asText());
        out.put("priceQuantity", estimate.path("priceQuantity").decimalValue());
        return out;
    }

    private Map<String, Object> readQuote(JsonNode quote) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("amount", quote.path("amount").asLong());
        out.put("currency", quote.path("currency").asText("UGX"));
        out.put("priceListItemId", quote.path("priceListItemId").asText(""));
        out.put("priceQuantity", quote.path("priceQuantity").decimalValue());
        out.put("expectedCompletion", quote.path("expectedCompletion").asText(""));
        out.put("sentAt", quote.path("sentAt").asText(""));
        out.put("sentBy", quote.path("sentBy").asText(""));
        return out;
    }

    private String paymentStatus(DepartmentOrder order) {
        if (order.getInvoiceId() == null) return "NOT_REQUESTED";
        syncInvoice(order);
        return switch (order.getInvoiceStatus()) {
            case "WAITING_FOR_CHECK" -> "PROOF_SUBMITTED";
            case "PAID", "RECEIPTED" -> "CONFIRMED";
            default -> "AWAITING_PROOF";
        };
    }

    private List<Map<String, Object>> readHistory(String existing) {
        List<Map<String, Object>> history = new ArrayList<>();
        for (JsonNode entry : parseHistory(existing)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("status", entry.path("stage").asText(""));
            item.put("by", entry.path("by").asText(""));
            item.put("at", entry.path("at").asText(""));
            item.put("note", entry.path("notes").isMissingNode() || entry.path("notes").isNull()
                    ? null : entry.path("notes").asText());
            history.add(item);
        }
        return history;
    }

    private JsonNode defaultQuality() {
        ArrayNode checklist = mapper.createArrayNode();
        checklist.addObject().put("id", "dimensions").put("label", "Dimensions and measurements checked").put("checked", false);
        checklist.addObject().put("id", "finish").put("label", "Finish and stitching inspected").put("checked", false);
        checklist.addObject().put("id", "colour").put("label", "Colour and design match").put("checked", false);
        checklist.addObject().put("id", "packaging").put("label", "Packaging complete").put("checked", false);
        return checklist;
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) return mapper.createObjectNode();
        try { return mapper.readTree(json); } catch (Exception e) { return mapper.createObjectNode(); }
    }

    private String writeJson(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { return "{}"; }
    }
}
