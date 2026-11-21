package org.unipod.unipoderpbackend.service.stock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.stock.CreateStockItemRequest;
import org.unipod.unipoderpbackend.exception.stock.InvalidStockException;
import org.unipod.unipoderpbackend.exception.stock.StockConflictException;
import org.unipod.unipoderpbackend.exception.stock.StockNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.StockItem;
import org.unipod.unipoderpbackend.model.operations.StockMovement;
import org.unipod.unipoderpbackend.model.stock.StockMovementDraft;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;
import org.unipod.unipoderpbackend.repository.operations.StockItemRepository;
import org.unipod.unipoderpbackend.repository.operations.StockMovementRepository;
import org.unipod.unipoderpbackend.repository.stock.OrderReferenceQuery;
import org.unipod.unipoderpbackend.repository.stock.StockMovementView;
import org.unipod.unipoderpbackend.repository.stock.StockQueryRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.operations.ConstraintViolations;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;
import org.unipod.unipoderpbackend.service.operations.ManagerNotifier;

/**
 * A department's stock list and its history. Every change to a balance goes through one transaction that holds
 * the item's row lock, so two people recording at once are served one after the other and stock never goes below
 * zero (the database check is the backstop).
 */
@Service
public class StockService {
    static final String ENTITY = "stock_item";
    private static final int HISTORY_LIMIT = 200;
    private static final Duration WEEK = Duration.ofDays(7);

    private final StockItemRepository items;
    private final StockMovementRepository movements;
    private final StockQueryRepository queries;
    private final OrderReferenceQuery orders;
    private final DepartmentAccess access;
    private final AuditService audit;
    private final ManagerNotifier notifier;
    private final Clock clock;

    public StockService(StockItemRepository items, StockMovementRepository movements, StockQueryRepository queries,
                        OrderReferenceQuery orders, DepartmentAccess access, AuditService audit,
                        ManagerNotifier notifier, Clock clock) {
        this.items = items;
        this.movements = movements;
        this.queries = queries;
        this.orders = orders;
        this.access = access;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** A movement a caller wants recorded; the reference is a request id (telecoms) or an order id (textile). */
    public record MovementCommand(UUID itemId, StockMovementType type, BigDecimal quantity, String note, String reference) {
    }

    public record Recorded(StockMovement movement, StockItem item, String itemName) {
    }

    @Transactional(readOnly = true)
    public List<StockItem> list(DepartmentCode department) {
        return queries.findByDepartmentOrdered(department.name());
    }

    /** The latest 200 movements of the department, newest first. */
    @Transactional(readOnly = true)
    public List<StockMovementView> history(DepartmentCode department) {
        return queries.findMovementViews(department.name(), PageRequest.of(0, HISTORY_LIMIT));
    }

    @Transactional(readOnly = true)
    public long movementsThisWeek(DepartmentCode department) {
        return queries.countMovementsSince(department.name(), clock.instant().minus(WEEK));
    }

    @Transactional
    public StockItem register(Actor actor, DepartmentCode department, CreateStockItemRequest request) {
        access.require(actor, department);
        Instant now = clock.instant();
        String sku = request.sku() == null || request.sku().isBlank()
                ? generatedSku(department) : request.sku().trim().toUpperCase(Locale.ROOT);
        if (queries.existsBySkuIgnoringCase(sku)) {
            throw skuTaken();
        }
        BigDecimal opening = request.quantity() == null ? BigDecimal.ZERO : request.quantity();
        StockItem item = new StockItem(UUID.randomUUID(), sku, request.name().trim(), department.name(), BigDecimal.ZERO,
                request.unit().trim(), request.minimum() == null ? BigDecimal.ZERO : request.minimum(),
                request.location() == null ? "" : request.location().trim(), now);
        try {
            items.saveAndFlush(item);
        } catch (DataIntegrityViolationException raced) {
            // Two people used the same SKU at once and the unique constraint decided; any other failure is not a clash.
            if (ConstraintViolations.named(raced, "uq_stock_items_sku")) {
                throw skuTaken();
            }
            throw raced;
        }
        audit.log(AuditEvent.created(actor, "stock.item_registered", ENTITY, item.getId(), StockAuditSnapshot.of(item)));
        if (item.isLow()) {
            announceLowStock(department, item);
        }
        if (opening.signum() > 0) {
            // The opening balance is a normal RECEIVED movement, so the history explains every unit on hand.
            StockMovement movement = item.record(new StockMovementDraft(StockMovementType.RECEIVED, opening,
                    "Opening balance", null, null, null), actor.email(), now);
            movements.save(movement);
            audit.log(AuditEvent.created(actor, "stock.movement_recorded", ENTITY, item.getId(),
                    StockAuditSnapshot.of(item, movement)));
        }
        return item;
    }

    @Transactional
    public Recorded record(Actor actor, DepartmentCode department, MovementCommand command) {
        access.require(actor, department);
        requireAllowed(department, command.type());
        StockItem item = lockInDepartment(command.itemId(), department);
        StockAuditSnapshot before = StockAuditSnapshot.of(item);
        boolean wasLow = item.isLow();
        Instant now = clock.instant();

        StockMovement movement = item.record(draftFor(department, command), actor.email(), now);
        items.save(item);
        movements.save(movement);

        audit.log(AuditEvent.changed(actor, "stock.movement_recorded", ENTITY, item.getId(), before,
                StockAuditSnapshot.of(item, movement)));
        if (!wasLow && item.isLow()) {
            announceLowStock(department, item);
        }
        return new Recorded(movement, item, item.getName());
    }

    @Transactional
    public StockItem changeMinimum(Actor actor, DepartmentCode department, UUID itemId, BigDecimal minimum) {
        access.require(actor, department);
        StockItem item = lockInDepartment(itemId, department);
        StockAuditSnapshot before = StockAuditSnapshot.of(item);
        boolean wasLow = item.isLow();

        item.changeMinimum(minimum, clock.instant());
        items.save(item);

        audit.log(AuditEvent.changed(actor, "stock.minimum_changed", ENTITY, item.getId(), before,
                StockAuditSnapshot.of(item)));
        if (!wasLow && item.isLow()) {
            announceLowStock(department, item);
        }
        return item;
    }

    private void announceLowStock(DepartmentCode department, StockItem item) {
        notifier.notifyManagers(department, NotificationType.LOW_STOCK, "Low stock: " + item.getName(),
                "Only " + item.getQuantityOnHand().stripTrailingZeros().toPlainString() + " " + item.getUnit() + " of "
                        + item.getName() + " left; the minimum is "
                        + item.getMinimum().stripTrailingZeros().toPlainString() + ".", ENTITY, item.getId());
    }

    private StockMovementDraft draftFor(DepartmentCode department, MovementCommand command) {
        // Only usage names the request or order it was used on; on received stock or a correction a reference is dropped.
        String reference = !command.type().isUsage() || command.reference() == null || command.reference().isBlank()
                ? null : command.reference().trim();
        if (reference != null && command.type() == StockMovementType.USED_ON_ORDER) {
            // Textile orders live in the orders code; when the reference is one of this department's orders, keep its
            // code and link. Otherwise it is kept as typed (D-045).
            Optional<OrderReferenceQuery.OrderRef> order = orders.find(department.name(), reference);
            if (order.isPresent()) {
                return new StockMovementDraft(command.type(), command.quantity(), command.note(), reference,
                        order.get().code(), order.get().id());
            }
        }
        return new StockMovementDraft(command.type(), command.quantity(), command.note(), reference, null, null);
    }

    /** Telecoms uses stock on requests; textile and CNC use it on orders; all may receive stock and correct a count. */
    private static void requireAllowed(DepartmentCode department, StockMovementType type) {
        Set<StockMovementType> allowed = EnumSet.of(StockMovementType.RECEIVED, StockMovementType.CORRECTION);
        if (department == DepartmentCode.TELECOMS) {
            allowed.add(StockMovementType.USED_ON_REQUEST);
        } else if (department == DepartmentCode.TEXTILE || department == DepartmentCode.CNC) {
            allowed.add(StockMovementType.USED_ON_ORDER);
        }
        if (!allowed.contains(type)) {
            throw new InvalidStockException("Stock in this department cannot be recorded as " + type + ". Use one of: "
                    + allowed + ".");
        }
    }

    /** Locks the row for the rest of the transaction; an item of another department is as good as missing. */
    private StockItem lockInDepartment(UUID itemId, DepartmentCode department) {
        return items.findByIdForUpdate(itemId)
                .filter(item -> department.name().equals(item.getDepartment()))
                .orElseThrow(StockNotFoundException::new);
    }

    private String generatedSku(DepartmentCode department) {
        String prefix = department.name().substring(0, 3);
        String sku;
        do {
            sku = prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        } while (queries.existsBySkuIgnoringCase(sku));
        return sku;
    }

    private static StockConflictException skuTaken() {
        return new StockConflictException("That SKU is already used by another stock item.");
    }
}
