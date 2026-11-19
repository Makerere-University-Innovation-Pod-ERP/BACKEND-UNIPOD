package org.unipod.unipoderpbackend.repository.stock;

import org.unipod.unipoderpbackend.model.operations.StockMovement;

/** A movement with the name of its item, so a history list needs one query and not one per row. */
public record StockMovementView(StockMovement movement, String itemName) {
}
