package org.unipod.unipoderpbackend.model.partner;

/** Derived from the MOU renewal date on read; only the last notified value is stored. */
public enum PartnerStatus {
    ACTIVE,
    RENEWAL_DUE,
    EXPIRING_SOON,
    EXPIRED
}
