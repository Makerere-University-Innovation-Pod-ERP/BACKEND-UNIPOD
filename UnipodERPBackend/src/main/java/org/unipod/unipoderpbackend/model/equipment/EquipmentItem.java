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
 * One kind of equipment a department owns, counted per type (D-008): the item says how many units exist and how many
 * are free right now. Loans reserve and release units here; fault reports change the state.
 */
@Entity
@Table(name = "equipment_items")
public class EquipmentItem {
    public static final int NAME_MAX = 255;
    public static final int CATEGORY_MAX = 100;
    public static final int ASSET_TAG_MAX = 64;
    public static final int LOCATION_MAX = 255;
    public static final int NOTE_MAX = 2000;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DepartmentCode department;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    @Column(nullable = false, length = CATEGORY_MAX)
    private String category;

    @Column(name = "asset_tag", nullable = false, length = ASSET_TAG_MAX)
    private String assetTag;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EquipmentState state;

    @Column(nullable = false, length = LOCATION_MAX)
    private String location;

    @Column(nullable = false, length = NOTE_MAX)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected EquipmentItem() {
    }

    public EquipmentItem(UUID id, DepartmentCode department, String name, String category, String assetTag,
                         int totalQuantity, String location, String note, Instant now) {
        if (totalQuantity < 1) {
            throw new InvalidEquipmentException("The total quantity must be at least 1.");
        }
        this.id = id;
        this.department = department;
        this.name = required(name, "The name");
        this.category = category == null ? "" : category.trim();
        this.assetTag = required(assetTag, "The asset tag");
        this.totalQuantity = totalQuantity;
        this.availableQuantity = totalQuantity;
        this.state = EquipmentState.AVAILABLE;
        this.location = location == null ? "" : location.trim();
        this.note = note == null ? "" : note.trim();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Faulty or in-maintenance equipment cannot be lent, and nobody can borrow more units than are free. */
    public void requireLendable(int units) {
        requirePositive(units);
        if (state.blocksLending()) {
            throw new EquipmentConflictException(name + " cannot be lent while it is "
                    + (state == EquipmentState.FAULTY ? "faulty." : "under maintenance."));
        }
        if (units > availableQuantity) {
            throw new EquipmentConflictException("Only " + availableQuantity + " of " + name + " "
                    + (availableQuantity == 1 ? "is" : "are") + " available.");
        }
    }

    /** Takes units out for a loan. */
    public void reserve(int units, Instant now) {
        requireLendable(units);
        availableQuantity -= units;
        if (availableQuantity == 0 && (state == EquipmentState.AVAILABLE || state == EquipmentState.IN_USE)) {
            state = EquipmentState.LOANED;
        }
        updatedAt = now;
    }

    /** Puts returned units back. A faulty item stays faulty: only a person decides it is fixed. */
    public void release(int units, Instant now) {
        requirePositive(units);
        if (availableQuantity + units > totalQuantity) {
            throw new EquipmentConflictException("More units of " + name + " would be back than the register holds.");
        }
        availableQuantity += units;
        if (state == EquipmentState.LOANED) {
            state = EquipmentState.AVAILABLE;
        }
        updatedAt = now;
    }

    /** A fault flags the whole type, as the screens do; the description becomes the visible note. */
    public void reportFault(String description, Instant now) {
        this.state = EquipmentState.FAULTY;
        this.note = required(description, "The description");
        this.updatedAt = now;
    }

    /**
     * Clears a fault or a maintenance state once a person has checked the equipment. Units still out on loan keep the
     * item LOANED when none are free.
     */
    public void markWorking(String newNote, Instant now) {
        if (state != EquipmentState.FAULTY && state != EquipmentState.UNDER_MAINTENANCE) {
            throw new EquipmentConflictException(name + " is not marked as faulty or under maintenance.");
        }
        state = availableQuantity == 0 ? EquipmentState.LOANED : EquipmentState.AVAILABLE;
        note = newNote == null || newNote.isBlank() ? "Marked working." : newNote.trim();
        updatedAt = now;
    }

    private static void requirePositive(int units) {
        if (units < 1) {
            throw new InvalidEquipmentException("The quantity must be at least 1.");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidEquipmentException(label + " is required.");
        }
        return value.trim();
    }

    public UUID getId() { return id; }
    public DepartmentCode getDepartment() { return department; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getAssetTag() { return assetTag; }
    public int getTotalQuantity() { return totalQuantity; }
    public int getAvailableQuantity() { return availableQuantity; }
    public EquipmentState getState() { return state; }
    public String getLocation() { return location; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
