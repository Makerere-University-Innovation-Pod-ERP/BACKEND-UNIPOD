package org.unipod.unipoderpbackend.dto.equipment;

import java.time.Instant;
import java.time.LocalDate;
import org.unipod.unipoderpbackend.model.equipment.EquipmentLoan;
import org.unipod.unipoderpbackend.model.equipment.LoanStatus;

/**
 * A loan as the telecoms lending desk reads it (`services/telecoms.js`, equipmentLoanSchema). The screen prints
 * `id` as the loan reference, so it is the loan's code, not its internal identifier.
 */
public record LoanResponse(
        String id,
        String equipmentId,
        String equipmentName,
        int quantity,
        String borrowerName,
        String borrowerType,
        String borrowerContact,
        LocalDate dateOut,
        LocalDate dateBack,
        String outgoingCondition,
        String incomingCondition,
        LoanStatus status,
        ManagerApproval managerApproval,
        Instant createdAt
) {
    /** The decision on the loan: who, when and why. It is set for approved and for declined loans. */
    public record ManagerApproval(String approvedBy, Instant approvedAt, String note) {
    }

    public static LoanResponse from(EquipmentLoan loan, String equipmentName, LocalDate today) {
        ManagerApproval decision = loan.getDecidedAt() == null ? null
                : new ManagerApproval(loan.getDecidedBy(), loan.getDecidedAt(), loan.getDecisionNote());
        return new LoanResponse(loan.getCode(), loan.getEquipmentId().toString(), equipmentName, loan.getQuantity(),
                loan.getBorrowerName(), loan.getBorrowerType(), loan.getBorrowerContact(), loan.getDateOut(),
                loan.getDateBack(), loan.getOutgoingCondition(), loan.getIncomingCondition(), loan.statusOn(today),
                decision, loan.getCreatedAt());
    }
}
