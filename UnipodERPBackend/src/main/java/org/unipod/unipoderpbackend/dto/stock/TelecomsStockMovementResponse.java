package org.unipod.unipoderpbackend.dto.stock;

import java.time.Instant;
import org.unipod.unipoderpbackend.model.operations.StockMovement;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;

/** A telecoms stock movement (`services/telecoms.js`, stockMovementSchema): `balance` and a nullable `requestId`. */
public record TelecomsStockMovementResponse(
        String id,
        String itemId,
        String itemName,
        StockMovementType type,
        double quantity,
        double balance,
        String note,
        String requestId,
        String by,
        Instant at
) {
    public static TelecomsStockMovementResponse from(StockMovement movement, String itemName) {
        return new TelecomsStockMovementResponse(movement.getId().toString(), movement.getStockItemId().toString(),
                itemName, movement.getMovementType(), MovementQuantity.of(movement),
                movement.getBalanceAfter().doubleValue(), movement.getReason(), movement.getReferenceId(),
                movement.getCreatedBy(), movement.getCreatedAt());
    }
}
