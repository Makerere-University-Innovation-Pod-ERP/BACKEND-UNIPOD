package org.unipod.unipoderpbackend.model.operations;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "timetable_blocks")
public class TimetableBlock {
    @Id private UUID id;
    @Column(name = "facility_id")
    private UUID facilityId;
    @Column(length = 32)
    private String department;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(name = "recurrence_rule", length = 255)
    private String recurrenceRule;
    @Column(name = "recurrence_until")
    private LocalDate recurrenceUntil;
    @Column(nullable = false, length = 32)
    private String source = "MANUAL";
    @Column(name = "created_by", nullable = false, length = 254)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TimetableBlock() {}
    public TimetableBlock(UUID id, UUID facilityId, String department, String title, Instant startsAt, Instant endsAt,
                          String recurrenceRule, LocalDate recurrenceUntil, String source, String createdBy, Instant now) {
        this.id = id; this.facilityId = facilityId; this.department = department; this.title = title;
        this.startsAt = startsAt; this.endsAt = endsAt; this.recurrenceRule = recurrenceRule;
        this.recurrenceUntil = recurrenceUntil; this.source = source == null ? "MANUAL" : source;
        this.createdBy = createdBy; this.createdAt = now; this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public UUID getFacilityId() { return facilityId; }
    public String getDepartment() { return department; }
    public String getTitle() { return title; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getRecurrenceRule() { return recurrenceRule; }
    public LocalDate getRecurrenceUntil() { return recurrenceUntil; }
    public String getSource() { return source; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}

