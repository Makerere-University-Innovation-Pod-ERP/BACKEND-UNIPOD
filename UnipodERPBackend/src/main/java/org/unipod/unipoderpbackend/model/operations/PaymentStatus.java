package org.unipod.unipoderpbackend.model.operations;

/** The payment states the department screens know, derived from the finance invoice and never stored. */
public enum PaymentStatus {
    NOT_REQUESTED,
    AWAITING_PROOF,
    PROOF_SUBMITTED,
    CONFIRMED,
    REJECTED;

    /**
     * @param invoiceStatus     the invoice's status name (WAITING_FOR_PROOF, WAITING_FOR_CHECK, PAID, RECEIPTED)
     * @param latestProofStatus the newest payment proof's status (PENDING, VERIFIED, REJECTED), or null when none
     */
    public static PaymentStatus fromInvoice(String invoiceStatus, String latestProofStatus) {
        return switch (invoiceStatus) {
            // A rejected proof sends the invoice back to "waiting for proof"; the screen should say it was rejected.
            case "WAITING_FOR_PROOF" -> "REJECTED".equals(latestProofStatus) ? REJECTED : AWAITING_PROOF;
            case "WAITING_FOR_CHECK" -> PROOF_SUBMITTED;
            case "PAID", "RECEIPTED" -> CONFIRMED;
            default -> NOT_REQUESTED;
        };
    }
}
