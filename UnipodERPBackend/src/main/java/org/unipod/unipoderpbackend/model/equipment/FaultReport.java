package org.unipod.unipoderpbackend.model.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A report that something is wrong with a piece of equipment or a machine. The description is free text: it is
 * kept here for the people who fix things and is deliberately never copied into the audit log.
 */
@Entity
@Table(name = "fault_reports")
public class FaultReport {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FaultKind kind;

    @Column(name = "equipment_id")
    private UUID equipmentId;

    @Column(name = "machine_id")
    private UUID machineId;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "photo_name", length = 255)
    private String photoName;

    @Column(name = "reported_by", nullable = false, length = 254)
    private String reportedBy;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    protected FaultReport() {
    }

    private FaultReport(UUID id, FaultKind kind, UUID equipmentId, UUID machineId, String description,
                        String photoName, String reportedBy, Instant reportedAt) {
        this.id = id;
        this.kind = kind;
        this.equipmentId = equipmentId;
        this.machineId = machineId;
        this.description = description.trim();
        this.photoName = photoName;
        this.reportedBy = reportedBy;
        this.reportedAt = reportedAt;
    }

    public static FaultReport forEquipment(UUID equipmentId, String description, String reportedBy, Instant now) {
        return new FaultReport(UUID.randomUUID(), FaultKind.FAULT, equipmentId, null, description, null, reportedBy, now);
    }

    public static FaultReport forMachine(UUID machineId, FaultKind kind, String description, String photoName,
                                         String reportedBy, Instant now) {
        return new FaultReport(UUID.randomUUID(), kind, null, machineId, description, photoName, reportedBy, now);
    }

    public UUID getId() { return id; }
    public FaultKind getKind() { return kind; }
    public UUID getEquipmentId() { return equipmentId; }
    public UUID getMachineId() { return machineId; }
    public String getDescription() { return description; }
    public String getPhotoName() { return photoName; }
    public String getReportedBy() { return reportedBy; }
    public Instant getReportedAt() { return reportedAt; }
}
