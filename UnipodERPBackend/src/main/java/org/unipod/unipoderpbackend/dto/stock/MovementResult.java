package org.unipod.unipoderpbackend.dto.stock;

/** What recording a movement returns: the new history line and the item with its new balance. */
public record MovementResult<M>(M movement, StockItemResponse item) {
}
