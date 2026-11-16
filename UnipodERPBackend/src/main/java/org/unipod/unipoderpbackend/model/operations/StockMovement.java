package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
public class StockMovement {
    @Id private UUID id;
    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;
    @Column(name = "department_order_id")
    private UUID departmentOrderId;
    @Column(nullable = false)
    private int delta;
    @Column(length = 255)
    private String reason;
    @Column(name = "created_by", nullable = false, length = 254)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockMovement() {}
    public StockMovement(UUID id, UUID stockItemId, UUID departmentOrderId, int delta, String reason, String createdBy, Instant createdAt) {
        this.id = id; this.stockItemId = stockItemId; this.departmentOrderId = departmentOrderId;
        this.delta = delta; this.reason = reason; this.createdBy = createdBy; this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
}
