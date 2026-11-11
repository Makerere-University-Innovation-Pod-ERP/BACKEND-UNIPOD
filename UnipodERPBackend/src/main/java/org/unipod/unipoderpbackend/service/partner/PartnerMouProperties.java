package org.unipod.unipoderpbackend.service.partner;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** How many days before the renewal date a partner moves into each warning status (D-006). */
@ConfigurationProperties("operations.partners")
public record PartnerMouProperties(
        @DefaultValue("60") int renewalDueDays,
        @DefaultValue("30") int expiringSoonDays) {

    public PartnerMouProperties {
        if (expiringSoonDays < 0 || renewalDueDays < expiringSoonDays) {
            throw new IllegalArgumentException(
                    "operations.partners needs 0 <= expiring-soon-days <= renewal-due-days.");
        }
    }
}
