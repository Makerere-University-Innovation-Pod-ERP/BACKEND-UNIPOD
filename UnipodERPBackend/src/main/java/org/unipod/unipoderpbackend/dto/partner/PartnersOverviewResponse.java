package org.unipod.unipoderpbackend.dto.partner;

import java.util.List;

/** {@code renewalDueCount} counts every partner whose status is not ACTIVE. */
public record PartnersOverviewResponse(
        long totalPartners,
        long renewalDueCount,
        List<PartnerResponse> partners) {
}
