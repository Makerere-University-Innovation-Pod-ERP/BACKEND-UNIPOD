package org.unipod.unipoderpbackend.service.partner;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;

/** Derives a partner's MOU status from its renewal date and the Kampala calendar day. */
@Component
public class PartnerStatusPolicy {
    private final PartnerMouProperties thresholds;
    private final Clock clock;

    public PartnerStatusPolicy(PartnerMouProperties thresholds, Clock clock) {
        this.thresholds = thresholds;
        this.clock = clock;
    }

    public PartnerStatus statusOf(LocalDate renewalDate) {
        if (renewalDate == null) {
            return PartnerStatus.ACTIVE;
        }
        // The shared clock is zoned Africa/Kampala, so "today" is the Kampala calendar day.
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(clock), renewalDate);
        if (daysLeft < 0) {
            return PartnerStatus.EXPIRED;
        }
        if (daysLeft <= thresholds.expiringSoonDays()) {
            return PartnerStatus.EXPIRING_SOON;
        }
        if (daysLeft <= thresholds.renewalDueDays()) {
            return PartnerStatus.RENEWAL_DUE;
        }
        return PartnerStatus.ACTIVE;
    }
}
