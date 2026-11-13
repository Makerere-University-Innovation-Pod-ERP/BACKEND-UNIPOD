package org.unipod.unipoderpbackend.model.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "facilities")
public class Facility {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Column(length = 160)
    private String location;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FacilityStatus status;

    @Column(length = 100)
    private String department;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Facility() {
    }

    public Facility(
            UUID id,
            String name,
            String location,
            int capacity,
            FacilityStatus status,
            String department,
            String createdBy,
            Instant now
    ) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.capacity = capacity;
        this.status = status;
        this.department = department;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateStatus(FacilityStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public int getCapacity() {
        return capacity;
    }

    public FacilityStatus getStatus() {
        return status;
    }

    public String getDepartment() {
        return department;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
