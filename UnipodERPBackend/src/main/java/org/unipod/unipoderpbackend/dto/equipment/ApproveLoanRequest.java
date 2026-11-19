package org.unipod.unipoderpbackend.dto.equipment;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.equipment.EquipmentLoan;

/** The optional note a manager gives when approving a loan. */
public record ApproveLoanRequest(@Size(max = EquipmentLoan.NOTE_MAX) @NoNullCharacters String note) {
}
