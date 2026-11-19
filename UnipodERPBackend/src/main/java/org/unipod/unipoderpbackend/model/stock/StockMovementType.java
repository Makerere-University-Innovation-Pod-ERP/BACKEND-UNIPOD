package org.unipod.unipoderpbackend.model.stock;

/**
 * The reasons stock changes, as the department screens list them. Telecoms uses USED_ON_REQUEST and
 * textile uses USED_ON_ORDER; the other departments' services decide which of them they accept.
 */
public enum StockMovementType {
    RECEIVED,
    USED_ON_REQUEST,
    USED_ON_ORDER,
    CORRECTION;

    public boolean isUsage() {
        return this == USED_ON_REQUEST || this == USED_ON_ORDER;
    }
}
