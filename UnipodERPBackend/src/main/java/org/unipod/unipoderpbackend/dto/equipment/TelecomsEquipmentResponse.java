package org.unipod.unipoderpbackend.dto.equipment;

import java.time.Instant;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;
import org.unipod.unipoderpbackend.model.equipment.EquipmentState;

/** The telecoms register row (frontend `services/telecoms.js`, equipmentItemSchema): no category. */
public record TelecomsEquipmentResponse(
        String id,
        String name,
        String assetTag,
        int totalQuantity,
        int availableQuantity,
        EquipmentState state,
        String location,
        String note,
        Instant updatedAt
) {
    public static TelecomsEquipmentResponse from(EquipmentItem item) {
        return new TelecomsEquipmentResponse(item.getId().toString(), item.getName(), item.getAssetTag(),
                item.getTotalQuantity(), item.getAvailableQuantity(), item.getState(), item.getLocation(),
                item.getNote(), item.getUpdatedAt());
    }
}
