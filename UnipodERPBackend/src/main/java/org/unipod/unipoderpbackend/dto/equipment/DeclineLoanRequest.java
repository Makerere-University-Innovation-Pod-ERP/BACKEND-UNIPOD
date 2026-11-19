package org.unipod.unipoderpbackend.dto.equipment;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.equipment.EquipmentLoan;

/** The optional reason a manager gives when declining a loan. */
public record DeclineLoanRequest(@Size(max = EquipmentLoan.NOTE_MAX) @NoNullCharacters String reason) {
}
