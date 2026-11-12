package org.unipod.unipoderpbackend.dto.partner;

import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;
import org.unipod.unipoderpbackend.model.partner.PartnerType;

public record PartnerResponse(
        UUID id,
        String name,
        PartnerType type,
        LocalDate renewalDate,
        PartnerStatus status,
        String contactPerson,
        String contactEmail,
        String notes,
        Long version) {

    public static PartnerResponse from(Partner partner, PartnerStatus status) {
        return new PartnerResponse(
                partner.getId(),
                partner.getName(),
                partner.getType(),
                partner.getRenewalDate(),
                status,
                partner.getContactPerson(),
                partner.getContactEmail(),
                partner.getNotes(),
                partner.getVersion());
    }
}
