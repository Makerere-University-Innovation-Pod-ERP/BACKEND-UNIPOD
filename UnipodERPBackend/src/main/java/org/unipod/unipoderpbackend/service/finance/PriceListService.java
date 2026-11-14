package org.unipod.unipoderpbackend.service.finance;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.finance.CreateInvoiceRequest;
import org.unipod.unipoderpbackend.dto.finance.InvoiceFromPriceRequest;
import org.unipod.unipoderpbackend.dto.finance.InvoiceResponse;
import org.unipod.unipoderpbackend.dto.finance.PriceListItemRequest;
import org.unipod.unipoderpbackend.dto.finance.PriceListItemResponse;
import org.unipod.unipoderpbackend.dto.finance.PriceListItemUpdateRequest;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationResource;
import org.unipod.unipoderpbackend.model.finance.InvoiceSourceType;
import org.unipod.unipoderpbackend.model.finance.PriceItemStatus;
import org.unipod.unipoderpbackend.model.finance.PriceListItem;
import org.unipod.unipoderpbackend.repository.finance.PriceListItemRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.authorization.AuthorizationService;
import org.unipod.unipoderpbackend.service.sector.SectorService;

@Service
public class PriceListService {
    static final String ENTITY = "price_list_item";

    private final PriceListItemRepository items;
    private final SectorService departments;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final InvoiceService invoices;
    private final Clock clock;

    public PriceListService(
            PriceListItemRepository items,
            SectorService departments,
            AuthorizationService authorization,
            AuditService audit,
            InvoiceService invoices,
            Clock clock
    ) {
        this.items = items;
        this.departments = departments;
        this.authorization = authorization;
        this.audit = audit;
        this.invoices = invoices;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PriceListItemResponse> list(Actor actor, UUID departmentId, PriceItemStatus status) {
        if (canManage(actor)) {
            return items.search(departmentId, status).stream().map(PriceListItemResponse::from).toList();
        }
        return items.search(departmentId, null).stream()
                .filter(item -> item.isActive() || canPropose(actor, item.getDepartmentId()))
                .filter(item -> status == null || item.getStatus() == status)
                .map(PriceListItemResponse::from)
                .toList();
    }

    @Transactional
    public PriceListItemResponse create(Actor actor, PriceListItemRequest request) {
        PriceItemStatus status;
        if (canManage(actor)) {
            status = PriceItemStatus.ACTIVE;
        } else {
            authorization.authorize(actor, AuthorizationAction.PRICE_LIST_PROPOSE, resource(request.departmentId()));
            status = PriceItemStatus.PROPOSED;
        }
        departments.requireActive(request.departmentId());
        Instant now = clock.instant();
        LocalDate effectiveFrom = request.effectiveFrom() == null
                ? LocalDate.ofInstant(now, clock.getZone())
                : request.effectiveFrom();
        PriceListItem item = items.saveAndFlush(new PriceListItem(
                UUID.randomUUID(),
                request.departmentId(),
                request.service().trim(),
                request.unit(),
                request.priceUgx(),
                effectiveFrom,
                status,
                actor.email(),
                now
        ));
        audit.log(new AuditEvent(actor, status == PriceItemStatus.ACTIVE ? "price_list_item.created" : "price_list_item.proposed",
                ENTITY, item.getId(), null, PriceListItemResponse.snapshot(item), item.getDepartmentId()));
        return PriceListItemResponse.from(item);
    }

    @Transactional
    public PriceListItemResponse update(Actor actor, UUID id, PriceListItemUpdateRequest request) {
        PriceListItem item = require(id);
        authorization.authorize(actor, AuthorizationAction.PRICE_LIST_MANAGE, resource(item.getDepartmentId()));
        var before = PriceListItemResponse.snapshot(item);
        item.change(
                request.service() == null ? null : request.service().trim(),
                request.unit(),
                request.priceUgx(),
                request.effectiveFrom(),
                request.status(),
                clock.instant()
        );
        items.saveAndFlush(item);
        audit.log(new AuditEvent(actor, "price_list_item.changed", ENTITY, item.getId(),
                before, PriceListItemResponse.snapshot(item), item.getDepartmentId()));
        return PriceListItemResponse.from(item);
    }

    @Transactional
    public InvoiceResponse invoice(Actor actor, UUID id, InvoiceFromPriceRequest request) {
        PriceListItem item = require(id);
        authorization.authorize(actor, AuthorizationAction.PRICE_LIST_MANAGE, resource(item.getDepartmentId()));
        return invoiceFor(id, request.customerEmail(), request.customerName(),
                request.quantity() == null ? 1 : request.quantity(), request.sourceType(), request.sourceId(), actor.email());
    }

    /**
     * For other modules (bookings, training, media): bill a customer from an active price.
     * The amount is the price times the quantity, and the invoice is created through the
     * finance module's InvoiceService, so it follows the normal proof, check and receipt steps.
     */
    @Transactional
    public InvoiceResponse invoiceFor(UUID priceItemId, String customerEmail, String customerName, int quantity,
                                      InvoiceSourceType sourceType, String sourceId, String issuedBy) {
        if (quantity < 1) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Quantity must be at least 1.");
        }
        PriceListItem item = requireActive(priceItemId);
        String department = departments.requireActive(item.getDepartmentId()).getName();
        String description = "%s x %d (%s)".formatted(
                item.getService(), quantity, item.getUnit().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        return invoices.create(new CreateInvoiceRequest(
                customerEmail.trim().toLowerCase(Locale.ROOT),
                customerName,
                department,
                description,
                Math.multiplyExact(item.getPriceUgx(), quantity),
                sourceType,
                sourceId
        ), issuedBy);
    }

    @Transactional(readOnly = true)
    public PriceListItem requireActive(UUID id) {
        PriceListItem item = require(id);
        if (!item.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This price list item is not active.");
        }
        return item;
    }

    private PriceListItem require(UUID id) {
        return items.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The price list item was not found."));
    }

    private boolean canManage(Actor actor) {
        return authorization.check(actor, AuthorizationAction.PRICE_LIST_MANAGE, resource(null)).allowed();
    }

    private boolean canPropose(Actor actor, UUID departmentId) {
        return authorization.check(actor, AuthorizationAction.PRICE_LIST_PROPOSE, resource(departmentId)).allowed();
    }

    private static AuthorizationResource resource(UUID departmentId) {
        return new AuthorizationResource(ENTITY, null, null, Set.of(), departmentId);
    }
}
