package org.unipod.unipoderpbackend.service.partner;

import java.time.LocalDate;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerType;

/** What the audit log keeps of a partner: plain values, never the entity. */
record PartnerAuditSnapshot(
        String name,
        PartnerType type,
        LocalDate renewalDate,
        String contactPerson,
        String contactEmail,
        String notes,
        String documentReference) {

    static PartnerAuditSnapshot of(Partner partner) {
        return new PartnerAuditSnapshot(
                partner.getName(),
                partner.getType(),
                partner.getRenewalDate(),
                partner.getContactPerson(),
                partner.getContactEmail(),
                partner.getNotes(),
                partner.getDocumentReference());
    }
}
