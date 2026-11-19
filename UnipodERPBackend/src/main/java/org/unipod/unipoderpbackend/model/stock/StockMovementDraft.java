package org.unipod.unipoderpbackend.model.stock;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a caller wants to record against a stock item. {@code quantity} is what a person types: positive for
 * received and used stock, signed for a correction. {@code referenceId} names the request or order the stock
 * was used on; it is kept as given because those records live in other modules.
 */
public record StockMovementDraft(
        StockMovementType type,
        BigDecimal quantity,
        String note,
        String referenceId,
        String referenceCode,
        UUID departmentOrderId
) {
}
