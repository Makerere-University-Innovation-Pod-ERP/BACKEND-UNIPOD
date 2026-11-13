package org.unipod.unipoderpbackend.model.finance;

public enum InvoiceStatus {
    WAITING_FOR_PROOF("Waiting for proof"),
    WAITING_FOR_CHECK("Waiting for check"),
    PAID("Paid"),
    RECEIPTED("Receipted");

    private final String label;

    InvoiceStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
