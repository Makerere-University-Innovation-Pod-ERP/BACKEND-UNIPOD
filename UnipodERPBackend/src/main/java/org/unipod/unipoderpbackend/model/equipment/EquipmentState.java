package org.unipod.unipoderpbackend.model.equipment;

public enum EquipmentState {
    AVAILABLE,
    IN_USE,
    LOANED,
    UNDER_MAINTENANCE,
    FAULTY;

    /** Faulty or in maintenance equipment cannot leave the lab. */
    public boolean blocksLending() {
        return this == FAULTY || this == UNDER_MAINTENANCE;
    }
}
