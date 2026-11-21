package org.unipod.unipoderpbackend.dto.stock;

import java.time.Instant;
import org.unipod.unipoderpbackend.model.operations.StockMovement;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;

public record CncStockMovementResponse(
        String id,
        String itemId,
        String itemName,
        StockMovementType type,
        double quantity,
        double balanceAfter,
        String jobId,
        String jobCode,
        String note,
        String by,
        Instant at
) {
    public static CncStockMovementResponse from(StockMovement movement, String itemName) {
        return new CncStockMovementResponse(movement.getId().toString(), movement.getStockItemId().toString(),
                itemName, movement.getMovementType(), MovementQuantity.of(movement),
                movement.getBalanceAfter().doubleValue(), movement.getReferenceId(), movement.getReferenceCode(),
                movement.getReason() == null ? "" : movement.getReason(), movement.getCreatedBy(), movement.getCreatedAt());
    }
}
