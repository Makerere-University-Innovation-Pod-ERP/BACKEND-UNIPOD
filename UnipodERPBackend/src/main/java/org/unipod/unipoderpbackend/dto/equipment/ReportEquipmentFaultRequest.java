package org.unipod.unipoderpbackend.dto.equipment;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;

public record ReportEquipmentFaultRequest(
        @NotNull UUID equipmentId,
        @NotBlank @Size(max = EquipmentItem.NOTE_MAX) @NoNullCharacters String description
) {
}
