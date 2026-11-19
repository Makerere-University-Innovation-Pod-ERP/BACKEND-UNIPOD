package org.unipod.unipoderpbackend.dto.equipment;

import java.time.Instant;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;
import org.unipod.unipoderpbackend.model.equipment.EquipmentState;

/** The multimedia register row (frontend `services/equipment.js`, equipmentItemSchema). */
public record EquipmentResponse(
        String id,
        String name,
        String category,
        String assetTag,
        int totalQuantity,
        int availableQuantity,
        EquipmentState state,
        String location,
        String note,
        Instant updatedAt
) {
    public static EquipmentResponse from(EquipmentItem item) {
        return new EquipmentResponse(item.getId().toString(), item.getName(), item.getCategory(), item.getAssetTag(),
                item.getTotalQuantity(), item.getAvailableQuantity(), item.getState(), item.getLocation(),
                item.getNote(), item.getUpdatedAt());
    }
}
