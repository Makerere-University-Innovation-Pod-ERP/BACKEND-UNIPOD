package org.unipod.unipoderpbackend.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_list_items")
public class PriceListItem {
    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 200)
    private String service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceUnit unit;

    @Column(name = "price_ugx", nullable = false)
    private long priceUgx;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceItemStatus status;

    @Column(name = "proposed_by", nullable = false, length = 254)
    private String proposedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PriceListItem() {
    }

    public PriceListItem(
            UUID id,
            UUID departmentId,
            String service,
            PriceUnit unit,
            long priceUgx,
            LocalDate effectiveFrom,
            PriceItemStatus status,
            String proposedBy,
            Instant now
    ) {
        this.id = id;
        this.departmentId = departmentId;
        this.service = service;
        this.unit = unit;
        this.priceUgx = priceUgx;
        this.effectiveFrom = effectiveFrom;
        this.status = status;
        this.proposedBy = proposedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void change(String newService, PriceUnit newUnit, Long newPrice, LocalDate newEffectiveFrom,
                       PriceItemStatus newStatus, Instant now) {
        if (newService != null) {
            this.service = newService;
        }
        if (newUnit != null) {
            this.unit = newUnit;
        }
        if (newPrice != null) {
            this.priceUgx = newPrice;
        }
        if (newEffectiveFrom != null) {
            this.effectiveFrom = newEffectiveFrom;
        }
        if (newStatus != null) {
            this.status = newStatus;
        }
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == PriceItemStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getService() {
        return service;
    }

    public PriceUnit getUnit() {
        return unit;
    }

    public long getPriceUgx() {
        return priceUgx;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public PriceItemStatus getStatus() {
        return status;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
