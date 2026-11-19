package org.unipod.unipoderpbackend.service.equipment;

import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;
import org.unipod.unipoderpbackend.model.equipment.EquipmentLoan;

/**
 * What the audit log keeps about a loan: ids, dates, quantities and statuses. The borrower's name and contact, the
 * condition notes and the manager's note are personal or free text and stay in the loan row (D-021, F3-D-027).
 */
record LoanAuditSnapshot(
        UUID equipmentId,
        String code,
        int quantity,
        String status,
        LocalDate dateOut,
        LocalDate dateBack,
        Integer availableQuantity
) {
    static LoanAuditSnapshot of(EquipmentLoan loan, EquipmentItem item) {
        return new LoanAuditSnapshot(loan.getEquipmentId(), loan.getCode(), loan.getQuantity(),
                loan.getStatus().name(), loan.getDateOut(), loan.getDateBack(),
                item == null ? null : item.getAvailableQuantity());
    }
}
