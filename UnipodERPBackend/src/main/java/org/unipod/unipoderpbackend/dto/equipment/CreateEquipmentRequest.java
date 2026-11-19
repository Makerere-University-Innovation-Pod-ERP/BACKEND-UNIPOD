package org.unipod.unipoderpbackend.dto.equipment;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;

/** Registers a new kind of equipment. The frontend has no screen for this yet; staff need it to fill the register. */
public record CreateEquipmentRequest(
        @NotBlank @Size(max = EquipmentItem.NAME_MAX) @NoNullCharacters String name,
        @Size(max = EquipmentItem.CATEGORY_MAX) @NoNullCharacters String category,
        @NotBlank @Size(max = EquipmentItem.ASSET_TAG_MAX) @NoNullCharacters String assetTag,
        @NotNull @Min(1) @Max(100000) Integer totalQuantity,
        @Size(max = EquipmentItem.LOCATION_MAX) @NoNullCharacters String location,
        @Size(max = EquipmentItem.NOTE_MAX) @NoNullCharacters String note
) {
}
