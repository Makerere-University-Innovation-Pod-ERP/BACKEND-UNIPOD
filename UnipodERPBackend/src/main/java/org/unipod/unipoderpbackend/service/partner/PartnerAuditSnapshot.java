package org.unipod.unipoderpbackend.service.partner;

import java.time.LocalDate;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerType;

/**
 * What the audit log keeps of a partner: plain values, never the entity. Contact details and the
 * free-text notes can hold personal data and the log is append-only, so only whether they changed
 * is recorded (D-021).
 */
record PartnerAuditSnapshot(
        String name,
        PartnerType type,
        LocalDate renewalDate,
        String documentReference,
        boolean contactChanged,
        boolean notesChanged) {

    static PartnerAuditSnapshot of(Partner partner, boolean contactChanged, boolean notesChanged) {
        return new PartnerAuditSnapshot(
                partner.getName(),
                partner.getType(),
                partner.getRenewalDate(),
                partner.getDocumentReference(),
                contactChanged,
                notesChanged);
    }
}
