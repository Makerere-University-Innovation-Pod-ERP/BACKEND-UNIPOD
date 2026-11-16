package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_items")
public class StockItem {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 64) private String sku;
    @Column(nullable = false, length = 255) private String name;
    @Column(length = 32) private String department;
    @Column(name = "quantity_on_hand", nullable = false) private int quantityOnHand;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;
    protected StockItem() {}
    public StockItem(UUID id, String sku, String name, String department, int qty, Instant now) {
        this.id = id; this.sku = sku; this.name = name; this.department = department; this.quantityOnHand = qty; this.updatedAt = now;
    }
    public void deduct(int qty, Instant now) { this.quantityOnHand = Math.max(0, this.quantityOnHand - Math.max(0, qty)); this.updatedAt = now; }
    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public int getQuantityOnHand() { return quantityOnHand; }
}

