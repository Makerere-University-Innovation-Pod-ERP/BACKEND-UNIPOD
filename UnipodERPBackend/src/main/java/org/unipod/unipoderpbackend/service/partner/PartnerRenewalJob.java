package org.unipod.unipoderpbackend.service.partner;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PartnerRenewalJob {
    private final PartnerRenewalNotifier notifier;

    PartnerRenewalJob(PartnerRenewalNotifier notifier) {
        this.notifier = notifier;
    }

    // Early morning in Kampala, so staff see the alert at the start of the working day.
    @Scheduled(cron = "${operations.partners.alert-cron:0 0 7 * * *}", zone = "Africa/Kampala")
    void run() {
        notifier.notifyDueRenewals();
    }
}
