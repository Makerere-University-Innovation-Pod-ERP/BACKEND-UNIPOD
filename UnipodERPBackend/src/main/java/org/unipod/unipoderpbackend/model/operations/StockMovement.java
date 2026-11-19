package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;

/** One line of a stock item's history. Rows are created by {@link StockItem#record} and never edited. */
@Entity
@Table(name = "stock_movements")
public class StockMovement {
    @Id private UUID id;
    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;
    @Column(name = "department_order_id")
    private UUID departmentOrderId;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal delta;
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", length = 24)
    private StockMovementType movementType;
    @Column(name = "balance_after", precision = 12, scale = 2)
    private BigDecimal balanceAfter;
    @Column(length = 1000)
    private String reason;
    @Column(name = "reference_id", length = 64)
    private String referenceId;
    @Column(name = "reference_code", length = 64)
    private String referenceCode;
    @Column(name = "created_by", nullable = false, length = 254)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockMovement() {}

    StockMovement(UUID id, UUID stockItemId, UUID departmentOrderId, BigDecimal delta, StockMovementType movementType,
                  BigDecimal balanceAfter, String reason, String referenceId, String referenceCode,
                  String createdBy, Instant createdAt) {
        this.id = id;
        this.stockItemId = stockItemId;
        this.departmentOrderId = departmentOrderId;
        this.delta = delta;
        this.movementType = movementType;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.referenceId = referenceId;
        this.referenceCode = referenceCode;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getStockItemId() { return stockItemId; }
    public UUID getDepartmentOrderId() { return departmentOrderId; }
    public BigDecimal getDelta() { return delta; }
    public StockMovementType getMovementType() { return movementType; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getReason() { return reason; }
    public String getReferenceId() { return referenceId; }
    public String getReferenceCode() { return referenceCode; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
