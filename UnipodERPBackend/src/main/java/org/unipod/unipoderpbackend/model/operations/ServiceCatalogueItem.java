package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_catalogue_items")
public class ServiceCatalogueItem {
    @Id private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DepartmentCode department;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 2000)
    private String description = "";
    @Column(nullable = false)
    private long price;
    @Column(nullable = false, length = 3)
    private String currency = "UGX";
    @Column(name = "turnaround_estimate", length = 120)
    private String turnaroundEstimate;
    @Column(name = "whatsapp_group_url", length = 500)
    private String whatsappGroupUrl;
    @Column(name = "requires_brief", nullable = false)
    private boolean requiresBrief;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceCatalogueItem() {}

    public ServiceCatalogueItem(UUID id, DepartmentCode department, String name, String description,
                                long price, String currency, String turnaroundEstimate,
                                String whatsappGroupUrl, boolean requiresBrief, boolean active,
                                int sortOrder, Instant now) {
        this.id = id; this.department = department; this.name = name;
        this.description = description == null ? "" : description;
        this.price = Math.max(0, price);
        this.currency = currency == null || currency.isBlank() ? "UGX" : currency;
        this.turnaroundEstimate = turnaroundEstimate;
        this.whatsappGroupUrl = whatsappGroupUrl;
        this.requiresBrief = requiresBrief; this.active = active; this.sortOrder = sortOrder;
        this.createdAt = now; this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public DepartmentCode getDepartment() { return department; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public long getPrice() { return price; }
    public String getCurrency() { return currency; }
    public String getTurnaroundEstimate() { return turnaroundEstimate; }
    public String getWhatsappGroupUrl() { return whatsappGroupUrl; }
    public boolean isRequiresBrief() { return requiresBrief; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
}

