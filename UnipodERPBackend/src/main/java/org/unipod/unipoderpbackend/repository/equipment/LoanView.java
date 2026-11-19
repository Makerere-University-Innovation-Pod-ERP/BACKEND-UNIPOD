package org.unipod.unipoderpbackend.repository.equipment;

import org.unipod.unipoderpbackend.model.equipment.EquipmentLoan;

/** A loan with the name of its equipment, so a list needs one query and not one per row. */
public record LoanView(EquipmentLoan loan, String equipmentName) {
}
