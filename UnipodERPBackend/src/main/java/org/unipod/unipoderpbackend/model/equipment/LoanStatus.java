package org.unipod.unipoderpbackend.model.equipment;

/**
 * Where an equipment loan stands. OVERDUE is never stored: it is what an ACTIVE loan is called once its return date
 * has passed ({@link EquipmentLoan#statusOn}). Approving a loan puts it straight on loan, so there is no stored
 * APPROVED step (D-043).
 */
public enum LoanStatus {
    PENDING_APPROVAL,
    DECLINED,
    ACTIVE,
    RETURNED,
    OVERDUE
}
