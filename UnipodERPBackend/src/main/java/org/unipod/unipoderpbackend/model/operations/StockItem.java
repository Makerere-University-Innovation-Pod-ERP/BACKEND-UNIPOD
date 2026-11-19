package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.stock.InvalidStockException;
import org.unipod.unipoderpbackend.model.stock.StockMovementDraft;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;

@Entity
@Table(name = "stock_items")
public class StockItem {
    private static final int SCALE = 2;
    private static final int MAX_NOTE = 1000;
    private static final int MAX_REFERENCE = 64;
    private static final BigDecimal MAX_BALANCE = new BigDecimal("9999999999.99");

    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 64) private String sku;
    @Column(nullable = false, length = 255) private String name;
    @Column(length = 32) private String department;
    @Column(name = "quantity_on_hand", nullable = false, precision = 12, scale = 2) private BigDecimal quantityOnHand;
    @Column(nullable = false, length = 32) private String unit;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal minimum;
    @Column(nullable = false, length = 255) private String location;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected StockItem() {}

    public StockItem(UUID id, String sku, String name, String department, BigDecimal quantity,
                     String unit, BigDecimal minimum, String location, Instant now) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.department = department;
        this.quantityOnHand = scaled(quantity);
        this.unit = unit;
        this.minimum = scaled(minimum);
        this.location = location;
        this.updatedAt = now;
    }

    /**
     * Applies a movement and returns the history row for it. Stock can never go below zero: the floor staff rely
     * on that, and the database check backs it up when two people record at once.
     */
    public StockMovement record(StockMovementDraft draft, String actor, Instant now) {
        BigDecimal delta = deltaOf(draft);
        BigDecimal balance = quantityOnHand.add(delta);
        if (balance.compareTo(MAX_BALANCE) > 0) {
            throw new InvalidStockException("The balance would be larger than the stock list can hold.");
        }
        if (balance.signum() < 0) {
            throw new InvalidStockException("Only " + quantityOnHand.stripTrailingZeros().toPlainString() + " " + unit
                    + " of " + name + " in stock, so quantity can never go below zero.");
        }
        this.quantityOnHand = balance;
        this.updatedAt = now;
        return new StockMovement(UUID.randomUUID(), id, draft.departmentOrderId(), delta, draft.type(), balance,
                blankToNull(draft.note()), blankToNull(draft.referenceId()), blankToNull(draft.referenceCode()),
                actor, now);
    }

    public void changeMinimum(BigDecimal newMinimum, Instant now) {
        BigDecimal value = scaled(newMinimum);
        if (value.signum() < 0) {
            throw new InvalidStockException("The minimum must be zero or more.");
        }
        this.minimum = value;
        this.updatedAt = now;
    }

    /** Low means strictly below the minimum, as the stock screens flag it. */
    public boolean isLow() {
        return quantityOnHand.compareTo(minimum) < 0;
    }

    private BigDecimal deltaOf(StockMovementDraft draft) {
        StockMovementType type = draft.type();
        BigDecimal quantity = scaled(draft.quantity());
        if (quantity.signum() == 0) {
            throw new InvalidStockException("Enter a quantity other than zero.");
        }
        if (type != StockMovementType.CORRECTION && quantity.signum() < 0) {
            throw new InvalidStockException("Enter a positive quantity.");
        }
        if (type == StockMovementType.CORRECTION && isBlank(draft.note())) {
            throw new InvalidStockException("A note is required for a correction.");
        }
        if (type.isUsage() && isBlank(draft.referenceId())) {
            throw new InvalidStockException(type == StockMovementType.USED_ON_ORDER
                    ? "Choose the order this stock was used on."
                    : "Choose the request this stock was used on.");
        }
        if (draft.note() != null && draft.note().trim().length() > MAX_NOTE) {
            throw new InvalidStockException("The note must be at most " + MAX_NOTE + " characters.");
        }
        if (draft.referenceId() != null && draft.referenceId().trim().length() > MAX_REFERENCE) {
            throw new InvalidStockException("The reference must be at most " + MAX_REFERENCE + " characters.");
        }
        return type.isUsage() ? quantity.negate() : quantity;
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public BigDecimal getQuantityOnHand() { return quantityOnHand; }
    public String getUnit() { return unit; }
    public BigDecimal getMinimum() { return minimum; }
    public String getLocation() { return location; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
