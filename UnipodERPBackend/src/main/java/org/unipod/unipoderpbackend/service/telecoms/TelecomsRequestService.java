package org.unipod.unipoderpbackend.service.telecoms;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsPaymentConfirmationRequest;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsPriceListItemResponse;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsQuoteRequest;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestListResponse;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestMessage;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestPayload;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestResponse;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestReturn;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestStatusRequest;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsWorkDetailsRequest;
import org.unipod.unipoderpbackend.exception.operations.DepartmentNotSetUpException;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.exception.operations.OperationsConflictException;
import org.unipod.unipoderpbackend.exception.operations.OperationsNotFoundException;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.finance.PriceItemStatus;
import org.unipod.unipoderpbackend.model.finance.PriceListItem;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.StockItem;
import org.unipod.unipoderpbackend.model.telecoms.TelecomsRequest;
import org.unipod.unipoderpbackend.model.telecoms.TelecomsRequestHistory;
import org.unipod.unipoderpbackend.model.telecoms.TelecomsRequestStatus;
import org.unipod.unipoderpbackend.repository.operations.DepartmentLookup;
import org.unipod.unipoderpbackend.repository.telecoms.TelecomsRequestHistoryRepository;
import org.unipod.unipoderpbackend.repository.telecoms.TelecomsRequestRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.finance.PriceListService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;
import org.unipod.unipoderpbackend.service.stock.StockService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TelecomsRequestService {
    private static final String ENTITY = "telecoms_request";
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");
    private static final Sort REQUEST_ORDER = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"));

    private final TelecomsRequestRepository requests;
    private final TelecomsRequestHistoryRepository history;
    private final DepartmentLookup departments;
    private final DepartmentAccess departmentAccess;
    private final PriceListService priceList;
    private final StockService stock;
    private final AuditTrail auditTrail;
    private final ObjectMapper mapper;
    private final Clock clock;

    public TelecomsRequestService(
            TelecomsRequestRepository requests,
            TelecomsRequestHistoryRepository history,
            DepartmentLookup departments,
            DepartmentAccess departmentAccess,
            PriceListService priceList,
            StockService stock,
            AuditTrail auditTrail,
            ObjectMapper mapper,
            Clock clock
    ) {
        this.requests = requests;
        this.history = history;
        this.departments = departments;
        this.departmentAccess = departmentAccess;
        this.priceList = priceList;
        this.stock = stock;
        this.auditTrail = auditTrail;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TelecomsRequestListResponse list(Actor actor, int page, int size) {
        checkDepartmentReadAccess(actor);
        Pageable pageable = PageRequest.of(page, size, REQUEST_ORDER);
        Page<TelecomsRequest> found = canViewAll(actor)
                ? requests.findAll(pageable)
                : requests.findAllByCreatedBy(actor.email(), pageable);
        List<TelecomsRequestResponse> items = found.getContent().stream()
                .map(request -> toResponse(request, List.of()))
                .toList();
        return new TelecomsRequestListResponse(
                items,
                found.getNumber(),
                found.getSize(),
                found.getTotalElements(),
                found.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public TelecomsRequestResponse get(UUID id, Actor actor) {
        TelecomsRequest request = require(id);
        checkCanView(request, actor);
        return toResponse(request, history.findAllByRequestIdInOrderByCreatedAtAscIdAsc(List.of(id)));
    }

    @Transactional
    public TelecomsRequestResponse create(TelecomsRequestPayload payload, Actor actor) {
        String estimate = serializeEstimate(payload.estimate());
        TelecomsRequestStatus status = Boolean.TRUE.equals(payload.draft())
                ? TelecomsRequestStatus.DRAFT
                : TelecomsRequestStatus.NEW;
        validatePayload(payload, status != TelecomsRequestStatus.DRAFT);
        Instant now = clock.instant();
        int year = LocalDate.ofInstant(now, KAMPALA).getYear();
        long sequence = requests.nextCodeSeq();
        String code = "TL-" + year + "-" + String.format(Locale.ROOT, "%06d", sequence);
        TelecomsRequest request = new TelecomsRequest(
                UUID.randomUUID(), code, payload, actor.email(), status, estimate, now);
        requests.saveAndFlush(request);
        addHistory(request, actor.email(), status == TelecomsRequestStatus.DRAFT
                ? "Request draft saved."
                : "Submitted request to Telecoms department.", now);
        auditTrail.record(actor.email(), "telecoms_request.created", ENTITY, request.getId(), null,
                AuditTrail.details("code", code, "status", status));
        return getAfterMutation(request);
    }

    @Transactional
    public TelecomsRequestResponse updateDraft(UUID id, TelecomsRequestPayload payload, Actor actor) {
        TelecomsRequest request = lock(id);
        checkCanEditDraft(request, actor);
        validatePayload(payload, false);
        TelecomsRequestStatus beforeStatus = request.getStatus();
        request.updateDraft(payload, serializeEstimate(payload.estimate()), clock.instant());
        requests.saveAndFlush(request);
        addHistory(request, actor.email(), "Draft updated.", request.getUpdatedAt());
        auditTrail.record(actor.email(), "telecoms_request.draft_updated", ENTITY, request.getId(),
                AuditTrail.details("status", beforeStatus), AuditTrail.details("status", request.getStatus()));
        return getAfterMutation(request);
    }

    @Transactional
    public TelecomsRequestResponse submitDraft(UUID id, Actor actor) {
        TelecomsRequest request = lock(id);
        checkCanEditDraft(request, actor);
        TelecomsRequestPayload payload = payloadFrom(request);
        validatePayload(payload, true);
        TelecomsRequestStatus beforeStatus = request.getStatus();
        request.submitDraft(payload, request.getEstimate(), clock.instant());
        requests.saveAndFlush(request);
        addHistory(request, actor.email(), "Submitted request to Telecoms department.", request.getUpdatedAt());
        auditTrail.record(actor.email(), "telecoms_request.submitted", ENTITY, request.getId(),
                AuditTrail.details("status", beforeStatus), AuditTrail.details("status", request.getStatus()));
        return getAfterMutation(request);
    }

    @Transactional
    public TelecomsRequestResponse sendQuote(UUID id, TelecomsQuoteRequest payload, Actor actor) {
        requireManager(actor);
        TelecomsRequest request = lock(id);
        PriceListItem price = requireTelecomsPrice(payload.priceListItemId());
        validateQuote(payload, price, request.getCustomerType());
        validateEstimatedParts(payload.estimate());
        TelecomsRequestStatus beforeStatus = request.getStatus();
        Instant now = clock.instant();
        QuoteData quote = new QuoteData(
                payload.amount(),
                "UGX",
                price.getId().toString(),
                payload.priceQuantity(),
                payload.expectedCompletion(),
                now,
                actor.email()
        );
        String estimate = serializeEstimate(payload.estimate());
        String quoteJson = serialize(quote, "quote");
        request.sendQuote(estimate, quoteJson, now);
        requests.saveAndFlush(request);
        addHistory(request, actor.email(),
                "Quote of UGX " + payload.amount().toPlainString()
                        + " sent to customer.", now);
        auditTrail.record(actor.email(), "telecoms_request.quoted", ENTITY, request.getId(),
                AuditTrail.details("status", beforeStatus),
                AuditTrail.details("status", request.getStatus(), "amount", payload.amount()));
        return getAfterMutation(request);
    }

    @Transactional
    public TelecomsRequestResponse confirmPayment(
            UUID id,
            TelecomsPaymentConfirmationRequest payload,
            Actor actor
    ) {
        requireFinance(actor);
        TelecomsRequest request = lock(id);
        Instant now = clock.instant();
        request.confirmPayment(payload.paymentReference(), now);
        requests.saveAndFlush(request);
        addHistory(request, actor.email(), "Payment confirmed by Finance.", now);
        auditTrail.record(actor.email(), "telecoms_request.payment_confirmed", ENTITY, request.getId(),
                AuditTrail.details("paymentStatus", request.getPaymentStatus()),
                AuditTrail.details("paymentStatus", request.getPaymentStatus(),
                        "reference", request.getPaymentReference()));
        return getAfterMutation(request);
    }

    @Transactional
    public TelecomsRequestResponse requestDetails(UUID id, TelecomsRequestMessage payload, Actor actor) {
        requireManager(actor);
        TelecomsRequest request = lock(id);
        TelecomsRequestStatus beforeStatus = request.getStatus();
        Instant now = clock.instant();
        request.requestDetails(now);
        requests.saveAndFlush(request);
        addHistory(request, actor.email(), payload.message().trim(), now);
        auditTrail.record(actor.email(), "telecoms_request.details_requested", ENTITY, request.getId(),
                AuditTrail.details("status", beforeStatus),
                AuditTrail.details("status", request.getStatus()));
        return getAfterMutation(request);
    }

    @Transactional
    public TelecomsRequestResponse sendBack(UUID id, TelecomsRequestReturn payload, Actor actor) {
        requireManager(actor);
        TelecomsRequest request = lock(id);
        TelecomsRequestStatus beforeStatus = request.getStatus();
        Instant now = clock.instant();
        request.sendBack(now);
        requests.saveAndFlush(request);
        addHistory(request, actor.email(), payload.reason().trim(), now);
        auditTrail.record(actor.email(), "telecoms_request.returned", ENTITY, request.getId(),
                AuditTrail.details("status", beforeStatus),
                AuditTrail.details("status", request.getStatus()));
        return getAfterMutation(request);
    }

    @Transactional
    public TelecomsRequestResponse updateStatus(UUID id, TelecomsRequestStatusRequest payload, Actor actor) {
        requireManager(actor);
        TelecomsRequest request = lock(id);
        TelecomsRequestStatus beforeStatus = request.getStatus();
        Instant now = clock.instant();
        request.moveTo(payload.toStatus(), now);
        requests.saveAndFlush(request);
        addHistory(request, actor.email(), "Moved to " + request.getStatus() + ".", now);
        auditTrail.record(actor.email(), "telecoms_request.status_changed", ENTITY, request.getId(),
                AuditTrail.details("status", beforeStatus),
                AuditTrail.details("status", request.getStatus()));
        return getAfterMutation(request);
    }

    @Transactional
    public TelecomsRequestResponse updateWorkDetails(
            UUID id,
            TelecomsWorkDetailsRequest payload,
            Actor actor
    ) {
        requireManager(actor);
        TelecomsRequest request = lock(id);
        request.updateWorkDetails(
                payload.technician().trim(),
                payload.partsUsed().trim(),
                payload.timeSpent().trim(),
                payload.notes().trim(),
                clock.instant()
        );
        requests.saveAndFlush(request);
        auditTrail.record(actor.email(), "telecoms_request.work_details_updated", ENTITY, request.getId(),
                AuditTrail.details("status", request.getStatus()),
                AuditTrail.details("status", request.getStatus(), "updatedAt", request.getUpdatedAt()));
        return getAfterMutation(request);
    }

    @Transactional
    public TelecomsRequestResponse markDone(UUID id, Actor actor) {
        requireManager(actor);
        TelecomsRequest request = lock(id);
        TelecomsRequestStatus beforeStatus = request.getStatus();
        Instant now = clock.instant();
        request.markDone(now);
        requests.saveAndFlush(request);
        addHistory(request, actor.email(), "Work completed and customer notified.", now);
        auditTrail.record(actor.email(), "telecoms_request.completed", ENTITY, request.getId(),
                AuditTrail.details("status", beforeStatus),
                AuditTrail.details("status", request.getStatus()));
        return getAfterMutation(request);
    }

    @Transactional(readOnly = true)
    public List<TelecomsPriceListItemResponse> priceList(Actor actor) {
        checkDepartmentReadAccess(actor);
        List<UUID> sectorIds = departments.sectorIds(DepartmentCode.TELECOMS);
        if (sectorIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, TelecomsPriceListItemResponse> items = new LinkedHashMap<>();
        for (UUID sectorId : sectorIds) {
            priceList.list(actor, sectorId, PriceItemStatus.ACTIVE).forEach(item ->
                    items.putIfAbsent(item.id(), new TelecomsPriceListItemResponse(
                            item.id(), item.service(), item.unit().name().toLowerCase(Locale.ROOT), item.priceUgx())));
        }
        return List.copyOf(items.values());
    }

    private TelecomsRequestResponse toResponse(TelecomsRequest request, List<TelecomsRequestHistory> historyEntries) {
        return new TelecomsRequestResponse(
                request.getId(),
                request.getCode(),
                request.getCustomerName(),
                request.getCustomerType(),
                request.getServiceWanted(),
                request.getEquipmentInvolved(),
                request.getPreferredDate(),
                request.isRulesAccepted(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                readEstimate(request.getEstimate()),
                readQuote(request.getQuote()),
                new TelecomsRequestResponse.Payment(
                        request.getPaymentStatus(), request.getPaymentReference(), request.getPaymentConfirmedAt()),
                new TelecomsRequestResponse.WorkDetails(
                        request.getTechnician(), request.getPartsUsed(), request.getTimeSpent(), request.getWorkNotes()),
                historyEntries.stream()
                        .map(entry -> new TelecomsRequestResponse.HistoryEntry(
                                entry.getStatus(), entry.getActor(), entry.getCreatedAt(), entry.getNote()))
                        .toList()
        );
    }

    private TelecomsRequestResponse getAfterMutation(TelecomsRequest request) {
        return toResponse(request, history.findAllByRequestIdInOrderByCreatedAtAscIdAsc(
                List.of(request.getId())));
    }

    private void addHistory(TelecomsRequest request, String actor, String note, Instant createdAt) {
        history.save(new TelecomsRequestHistory(
                UUID.randomUUID(), request.getId(), request.getStatus(), actor, createdAt, note));
    }

    private TelecomsRequest require(UUID id) {
        return requests.findById(id)
                .orElseThrow(() -> new OperationsNotFoundException("Telecoms request not found."));
    }

    private TelecomsRequest lock(UUID id) {
        return requests.findByIdForUpdate(id)
                .orElseThrow(() -> new OperationsNotFoundException("Telecoms request not found."));
    }

    private void checkDepartmentReadAccess(Actor actor) {
        departmentAccess.require(actor, DepartmentCode.TELECOMS);
    }

    private void checkCanView(TelecomsRequest request, Actor actor) {
        checkDepartmentReadAccess(actor);
        if (!canViewAll(actor) && !actor.email().equals(request.getCreatedBy())) {
            throw new OperationsNotFoundException("Telecoms request not found.");
        }
    }

    private static boolean canViewAll(Actor actor) {
        return isManager(actor) || actor.role() == ApiUserRole.FINANCE_OFFICER;
    }

    private void checkCanEditDraft(TelecomsRequest request, Actor actor) {
        if (isManager(actor)) {
            requireTelecomsManager(actor);
            return;
        }
        if (!actor.email().equals(request.getCreatedBy())) {
            throw new OperationsNotFoundException("Telecoms request not found.");
        }
    }

    private void requireManager(Actor actor) {
        if (!isManager(actor)) {
            throw new AccessDeniedException("Only Telecoms managers can perform this action.");
        }
        requireTelecomsManager(actor);
    }

    private void requireFinance(Actor actor) {
        if (actor.role() != ApiUserRole.FINANCE_OFFICER && actor.role() != ApiUserRole.ADMINISTRATOR) {
            throw new AccessDeniedException("Only Finance Officers can confirm payment.");
        }
    }

    private void requireTelecomsManager(Actor actor) {
        if (actor.role() == ApiUserRole.TELECOMS_DOMAIN_OWNER) {
            return;
        }
        departmentAccess.requireManage(actor, DepartmentCode.TELECOMS);
    }

    private static boolean isManager(Actor actor) {
        return actor.role() == ApiUserRole.ADMINISTRATOR
                || actor.role() == ApiUserRole.DEPARTMENT_COORDINATOR
                || actor.role() == ApiUserRole.TELECOMS_DOMAIN_OWNER;
    }

    private void validatePayload(TelecomsRequestPayload payload, boolean submitted) {
        if (payload.customerType() != null
                && !List.of("Student", "Public", "Company", "Research Group", "Internal")
                        .contains(payload.customerType())) {
            throw new InvalidOperationsRequestException("Choose a valid customer category.");
        }
        if (!submitted) {
            return;
        }
        if (isBlank(payload.customerName())
                || isBlank(payload.serviceWanted())
                || isBlank(payload.equipmentInvolved())
                || !payload.rulesAccepted()) {
            throw new InvalidOperationsRequestException(
                    "Customer name, service details, equipment involved, and acceptance of the lab rules are required.");
        }
        if (payload.preferredDate() != null && payload.preferredDate().isBefore(LocalDate.now(clock.withZone(KAMPALA)))) {
            throw new InvalidOperationsRequestException("The preferred date cannot be in the past.");
        }
    }

    private void validateQuote(TelecomsQuoteRequest payload, PriceListItem price, String customerType) {
        if (payload.estimate().parts() == null) {
            throw new InvalidOperationsRequestException("Parts estimate must be an array.");
        }
        if (!payload.priceListItemId().equals(payload.estimate().priceListItemId())
                || payload.estimate().priceQuantity() == null
                || payload.priceQuantity().compareTo(payload.estimate().priceQuantity()) != 0) {
            throw new InvalidOperationsRequestException(
                    "The estimate price item and quantity must match the selected quote.");
        }
        BigDecimal expectedAmount = BigDecimal.valueOf(price.getPriceUgx())
                .multiply(payload.priceQuantity());
        if (expectedAmount.compareTo(payload.amount()) != 0) {
            throw new InvalidOperationsRequestException(
                    "The quote amount must match the selected Telecoms price and quantity.");
        }
        if (payload.expectedCompletion().isBefore(LocalDate.now(clock.withZone(KAMPALA)))) {
            throw new InvalidOperationsRequestException("The expected completion date cannot be in the past.");
        }
        if (customerType == null || customerType.isBlank()) {
            throw new InvalidOperationsRequestException("The request customer category is missing.");
        }
    }

    private void validateEstimatedParts(TelecomsRequestPayload.Estimate estimate) {
        Map<UUID, BigDecimal> requested = new LinkedHashMap<>();
        for (TelecomsRequestPayload.Estimate.PartEstimate part : estimate.parts()) {
            if (part == null) {
                throw new InvalidOperationsRequestException("A part estimate cannot be empty.");
            }
            UUID itemId = parseUuid(part.itemId(), "A part estimate contains an invalid stock item ID.");
            requested.merge(itemId, part.quantity(), BigDecimal::add);
        }
        Map<UUID, StockItem> telecomsStock = stock.list(DepartmentCode.TELECOMS).stream()
                .collect(Collectors.toMap(StockItem::getId, item -> item));
        for (Map.Entry<UUID, BigDecimal> line : requested.entrySet()) {
            StockItem item = telecomsStock.get(line.getKey());
            if (item == null) {
                throw new OperationsNotFoundException("A part estimate references a stock item outside Telecoms.");
            }
            if (line.getValue().compareTo(item.getQuantityOnHand()) > 0) {
                throw new OperationsConflictException(
                        "INSUFFICIENT_STOCK",
                        "There is not enough " + item.getName() + " available to quote this request."
                );
            }
        }
    }

    private PriceListItem requireTelecomsPrice(String rawId) {
        UUID id = parseUuid(rawId, "A valid Telecoms price list item is required.");
        PriceListItem item = priceList.requireActive(id);
        List<UUID> sectorIds = departments.sectorIds(DepartmentCode.TELECOMS);
        if (sectorIds.isEmpty()) {
            throw new DepartmentNotSetUpException("The Telecoms department has not been set up yet.");
        }
        if (!sectorIds.contains(item.getDepartmentId())) {
            throw new OperationsNotFoundException("Telecoms price list item not found.");
        }
        return item;
    }

    private TelecomsRequestPayload payloadFrom(TelecomsRequest request) {
        return new TelecomsRequestPayload(
                request.getCustomerName(),
                request.getCustomerType(),
                request.getServiceWanted(),
                request.getEquipmentInvolved(),
                request.getPreferredDate(),
                request.isRulesAccepted(),
                readPayloadEstimate(request.getEstimate()),
                false
        );
    }

    private String serializeEstimate(TelecomsRequestPayload.Estimate estimate) {
        return estimate == null ? null : serialize(estimate, "estimate");
    }

    private String serialize(Object value, String label) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new InvalidOperationsRequestException(
                    "TELECOMS_REQUEST_INVALID", "Could not save Telecoms request " + label + ".", exception);
        }
    }

    private TelecomsRequestResponse.Estimate readEstimate(String json) {
        TelecomsRequestPayload.Estimate estimate = readPayloadEstimate(json);
        if (estimate == null) {
            return null;
        }
        return new TelecomsRequestResponse.Estimate(
                estimate.parts() == null ? List.of() : estimate.parts().stream()
                        .map(part -> new TelecomsRequestResponse.PartEstimate(part.itemId(), part.quantity()))
                        .toList(),
                estimate.priceListItemId(),
                estimate.priceQuantity() == null ? BigDecimal.ONE : estimate.priceQuantity()
        );
    }

    private TelecomsRequestPayload.Estimate readPayloadEstimate(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, TelecomsRequestPayload.Estimate.class);
        } catch (JacksonException exception) {
            throw new InvalidOperationsRequestException(
                    "TELECOMS_REQUEST_INVALID", "Could not read the saved Telecoms estimate.", exception);
        }
    }

    private TelecomsRequestResponse.Quote readQuote(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            QuoteData quote = mapper.readValue(json, QuoteData.class);
            return new TelecomsRequestResponse.Quote(
                    quote.amount(),
                    quote.currency(),
                    quote.priceListItemId(),
                    quote.priceQuantity(),
                    quote.expectedCompletion(),
                    quote.sentAt(),
                    quote.sentBy()
            );
        } catch (JacksonException exception) {
            throw new InvalidOperationsRequestException(
                    "TELECOMS_REQUEST_INVALID", "Could not read the saved Telecoms quote.", exception);
        }
    }

    private static UUID parseUuid(String value, String message) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new InvalidOperationsRequestException(message);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record QuoteData(
            BigDecimal amount,
            String currency,
            String priceListItemId,
            BigDecimal priceQuantity,
            LocalDate expectedCompletion,
            Instant sentAt,
            String sentBy
    ) {
    }
}
