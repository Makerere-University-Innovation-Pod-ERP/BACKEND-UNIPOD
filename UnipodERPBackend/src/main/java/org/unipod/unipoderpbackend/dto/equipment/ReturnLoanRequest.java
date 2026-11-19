package org.unipod.unipoderpbackend.dto.equipment;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.equipment.EquipmentLoan;

/** The inspection notes recorded when the equipment comes back. */
public record ReturnLoanRequest(@NotBlank @Size(max = EquipmentLoan.RETURN_CONDITION_MAX) @NoNullCharacters String condition) {
}
