package org.unipod.unipoderpbackend.model.equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.exception.equipment.EquipmentConflictException;
import org.unipod.unipoderpbackend.exception.equipment.InvalidEquipmentException;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

/**
 * A workshop machine. It is kept apart from equipment because it has no quantity or asset tag and its states are
 * about being fit to book: a machine that is not WORKING is not offered for use.
 */
@Entity
@Table(name = "machines")
public class Machine {
    public static final int NAME_MAX = 255;
    public static final int NOTE_MAX = 2000;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DepartmentCode department;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MachineState state;

    @Column(nullable = false, length = NOTE_MAX)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Machine() {
    }

    public Machine(UUID id, DepartmentCode department, String name, MachineState state, String note, Instant now) {
        if (name == null || name.isBlank()) {
            throw new InvalidEquipmentException("The name is required.");
        }
        this.id = id;
        this.department = department;
        this.name = name.trim();
        this.state = state == null ? MachineState.WORKING : state;
        this.note = note == null ? "" : note.trim();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * A fault always flags the machine, unless it is already being repaired. A service request only flags a machine
     * that is working, so it never hides a fault that was reported earlier.
     */
    public void report(FaultKind kind, String description, Instant now) {
        if (description == null || description.isBlank()) {
            throw new InvalidEquipmentException("Describe the fault or service needed.");
        }
        if (kind == FaultKind.FAULT && state != MachineState.BEING_REPAIRED) {
            state = MachineState.FAULT_REPORTED;
        } else if (kind == FaultKind.SERVICE && state == MachineState.WORKING) {
            state = MachineState.SERVICE_DUE;
        }
        note = description.trim();
        updatedAt = now;
    }

    public void markWorking(String newNote, Instant now) {
        if (state == MachineState.WORKING) {
            throw new EquipmentConflictException(name + " is already marked as working.");
        }
        state = MachineState.WORKING;
        note = newNote == null || newNote.isBlank() ? "Marked working." : newNote.trim();
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public DepartmentCode getDepartment() { return department; }
    public String getName() { return name; }
    public MachineState getState() { return state; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
