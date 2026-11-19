package org.unipod.unipoderpbackend.dto.stock;

import java.time.Instant;
import org.unipod.unipoderpbackend.model.operations.StockMovement;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;

/** A textile stock movement (`services/textile.js`, stockMovementSchema): `balanceAfter`, order reference, a note that is never null. */
public record TextileStockMovementResponse(
        String id,
        String itemId,
        String itemName,
        StockMovementType type,
        double quantity,
        double balanceAfter,
        String orderId,
        String orderCode,
        String note,
        String by,
        Instant at
) {
    public static TextileStockMovementResponse from(StockMovement movement, String itemName) {
        return new TextileStockMovementResponse(movement.getId().toString(), movement.getStockItemId().toString(),
                itemName, movement.getMovementType(), MovementQuantity.of(movement),
                movement.getBalanceAfter().doubleValue(), movement.getReferenceId(), movement.getReferenceCode(),
                movement.getReason() == null ? "" : movement.getReason(), movement.getCreatedBy(), movement.getCreatedAt());
    }
}
