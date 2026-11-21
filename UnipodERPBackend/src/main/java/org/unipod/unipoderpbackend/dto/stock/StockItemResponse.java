package org.unipod.unipoderpbackend.dto.stock;

import org.unipod.unipoderpbackend.model.operations.StockItem;

/** A stock line as the CNC, telecoms and textile stock screens read it (`stockItemSchema`). Quantities may be fractional. */
public record StockItemResponse(String id, String name, String unit, double quantity, double minimum, String location) {
    public static StockItemResponse from(StockItem item) {
        return new StockItemResponse(item.getId().toString(), item.getName(), item.getUnit(),
                item.getQuantityOnHand().doubleValue(), item.getMinimum().doubleValue(), item.getLocation());
    }
}
