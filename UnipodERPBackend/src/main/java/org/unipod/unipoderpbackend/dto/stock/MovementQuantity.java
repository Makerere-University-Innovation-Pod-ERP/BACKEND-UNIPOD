package org.unipod.unipoderpbackend.dto.stock;

import org.unipod.unipoderpbackend.model.operations.StockMovement;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;

/** The quantity a person typed: positive for received and used stock, signed for a correction. */
final class MovementQuantity {
    private MovementQuantity() {
    }

    static double of(StockMovement movement) {
        return movement.getMovementType() == StockMovementType.CORRECTION
                ? movement.getDelta().doubleValue()
                : movement.getDelta().abs().doubleValue();
    }
}
