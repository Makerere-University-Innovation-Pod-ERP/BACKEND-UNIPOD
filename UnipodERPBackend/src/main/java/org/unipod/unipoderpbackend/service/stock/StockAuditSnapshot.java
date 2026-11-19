package org.unipod.unipoderpbackend.service.stock;

import java.math.BigDecimal;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.operations.StockItem;
import org.unipod.unipoderpbackend.model.operations.StockMovement;

/**
 * What the audit log keeps about stock: the SKU, the balance, the minimum and the movement's type and id. Item
 * names, notes and the request or order reference are free text and stay out (D-021, F3-D-027).
 */
record StockAuditSnapshot(
        String department,
        String sku,
        BigDecimal quantityOnHand,
        BigDecimal minimum,
        String movementType,
        UUID movementId
) {
    static StockAuditSnapshot of(StockItem item) {
        return new StockAuditSnapshot(item.getDepartment(), item.getSku(), item.getQuantityOnHand(), item.getMinimum(),
                null, null);
    }

    static StockAuditSnapshot of(StockItem item, StockMovement movement) {
        return new StockAuditSnapshot(item.getDepartment(), item.getSku(), item.getQuantityOnHand(), item.getMinimum(),
                movement.getMovementType().name(), movement.getId());
    }
}
