package org.unipod.unipoderpbackend.service.equipment;

import java.util.UUID;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;

/**
 * What the audit log keeps about equipment: identifiers, counts and the state. Names, notes and fault descriptions
 * are free text, and the log is append-only, so none of them goes in (D-021, F3-D-027).
 */
record EquipmentAuditSnapshot(
        String department,
        String assetTag,
        int totalQuantity,
        int availableQuantity,
        String state,
        UUID reportId
) {
    static EquipmentAuditSnapshot of(EquipmentItem item) {
        return of(item, null);
    }

    static EquipmentAuditSnapshot of(EquipmentItem item, UUID reportId) {
        return new EquipmentAuditSnapshot(item.getDepartment().name(), item.getAssetTag(), item.getTotalQuantity(),
                item.getAvailableQuantity(), item.getState().name(), reportId);
    }
}
