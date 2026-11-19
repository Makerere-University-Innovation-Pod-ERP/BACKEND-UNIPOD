package org.unipod.unipoderpbackend.dto.equipment;

import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;

/** The optional note a manager leaves when putting faulty equipment back in service. */
public record MarkEquipmentWorkingRequest(@Size(max = EquipmentItem.NOTE_MAX) @NoNullCharacters String note) {
}
