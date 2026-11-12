package org.unipod.unipoderpbackend.service.partner;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;
import org.unipod.unipoderpbackend.repository.partner.AlertRecipientQuery;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

/**
 * Tells the alert recipients when a partner's MOU enters a warning status. Each status change is
 * announced once: the row remembers the last status it was announced for, and a partner that
 * returns to ACTIVE (a renewal) is reset so its next warning is announced again.
 */
@Service
public class PartnerRenewalNotifier {
    private static final Logger log = LoggerFactory.getLogger(PartnerRenewalNotifier.class);
    private static final int TITLE_NAME_MAX = 120;

    private final PartnerRepository partners;
    private final PartnerStatusPolicy statusPolicy;
    private final PartnerMouProperties properties;
    private final AlertRecipientQuery recipientQuery;
    private final NotificationService notifications;
    private final TransactionTemplate transaction;

    public PartnerRenewalNotifier(
            PartnerRepository partners,
            PartnerStatusPolicy statusPolicy,
            PartnerMouProperties properties,
            AlertRecipientQuery recipientQuery,
            NotificationService notifications,
            PlatformTransactionManager transactionManager) {
        this.partners = partners;
        this.statusPolicy = statusPolicy;
        this.properties = properties;
        this.recipientQuery = recipientQuery;
        this.notifications = notifications;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Returns how many partners were announced in this run. */
    public int notifyDueRenewals() {
        Set<String> recipients = recipients();
        if (recipients.isEmpty()) {
            // Nothing is marked as notified, so alerts begin once someone is configured (D-020).
            log.warn("No MOU alert recipients: set operations.partners.alert-roles or alert-recipients.");
            return 0;
        }
        int announced = 0;
        for (Partner partner : partners.findAllByRenewalOrder()) {
            // The list and status are read before the claim; a renewal landing in between can at worst
            // send one alert for the old status, and the claim still stops any duplicate.
            PartnerStatus status = statusPolicy.statusOf(partner.getRenewalDate());
            if (!needsAttention(partner.getLastNotifiedStatus(), status)) {
                continue;
            }
            try {
                // One transaction per partner: a failure for one does not hold back the others.
                Integer sent = transaction.execute(txStatus -> announce(partner, status, recipients));
                announced += sent == null ? 0 : sent;
            } catch (RuntimeException failure) {
                log.error("MOU alert failed for partner {}", partner.getId(), failure);
            }
        }
        return announced;
    }

    private static boolean needsAttention(PartnerStatus lastNotified, PartnerStatus status) {
        if (status == lastNotified) {
            return false;
        }
        return status != PartnerStatus.ACTIVE || lastNotified != null;
    }

    private int announce(Partner partner, PartnerStatus status, Set<String> recipients) {
        if (partners.claimNotification(partner.getId(), status) == 0) {
            return 0;
        }
        if (status == PartnerStatus.ACTIVE) {
            return 0;
        }
        notifications.publish(new NotificationEvent(
                NotificationType.AGREEMENT_EXPIRING,
                recipients,
                title(partner, status),
                body(partner, status),
                "partner",
                partner.getId()));
        return 1;
    }

    private Set<String> recipients() {
        Set<String> recipients = new HashSet<>(properties.alertRecipients());
        recipients.addAll(recipientQuery.activeEmailsWithRole(properties.alertRoles()));
        return recipients;
    }

    private static String title(Partner partner, PartnerStatus status) {
        String name = abbreviate(partner.getName());
        return switch (status) {
            case RENEWAL_DUE -> "MOU with " + name + " is due for renewal";
            case EXPIRING_SOON -> "MOU with " + name + " expires soon";
            case EXPIRED -> "MOU with " + name + " has expired";
            case ACTIVE -> throw new IllegalStateException("ACTIVE partners are not announced.");
        };
    }

    private static String body(Partner partner, PartnerStatus status) {
        String verb = status == PartnerStatus.EXPIRED ? "expired on" : "renewal date is";
        String type = partner.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return "The MOU with " + partner.getName() + " (" + type + ") " + verb + " "
                + partner.getRenewalDate() + ".";
    }

    private static String abbreviate(String name) {
        return name.length() <= TITLE_NAME_MAX ? name : name.substring(0, TITLE_NAME_MAX - 1) + "…";
    }
}
