package org.unipod.unipoderpbackend.service.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.unipod.unipoderpbackend.repository.partner.AlertRecipientQuery;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

/**
 * Not transactional, so each partner's own transaction really commits or rolls back. The job sees
 * only the two partners seeded here, which keeps real data in a shared dev database untouched.
 */
@SpringBootTest
class PartnerRenewalNotifierFailureTest {
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    @Autowired
    private PartnerRepository partners;

    @Autowired
    private AlertRecipientQuery recipientQuery;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteSeededPartners() {
        partners.deleteAllById(created);
    }

    @Test
    void aFailureForOnePartnerRollsBackOnlyThatPartnerAndTheOthersAreAnnounced() {
        Partner failing = seed("Failing", TODAY.plusDays(10));
        Partner healthy = seed("Healthy", TODAY.plusDays(45));
        NotificationService notifications = mock(NotificationService.class);
        doThrow(new IllegalStateException("notification store unavailable"))
                .when(notifications)
                .publish(ArgumentMatchers.argThat(event -> failing.getId().equals(event.entityId())));

        int announced = notifier(notifications, List.of(failing, healthy)).notifyDueRenewals();

        assertEquals(1, announced);
        assertNull(partners.findById(failing.getId()).orElseThrow().getLastNotifiedStatus());
        assertEquals(PartnerStatus.RENEWAL_DUE,
                partners.findById(healthy.getId()).orElseThrow().getLastNotifiedStatus());
        verify(notifications, times(2)).publish(any(NotificationEvent.class));
    }

    @Test
    void aFailedPartnerIsRetriedOnTheNextRun() {
        Partner failing = seed("Retried", TODAY.plusDays(10));
        NotificationService broken = mock(NotificationService.class);
        doThrow(new IllegalStateException("notification store unavailable"))
                .when(broken).publish(any(NotificationEvent.class));
        notifier(broken, List.of(failing)).notifyDueRenewals();
        NotificationService working = mock(NotificationService.class);

        int announced = notifier(working, List.of(failing)).notifyDueRenewals();

        assertEquals(1, announced);
        assertEquals(PartnerStatus.EXPIRING_SOON,
                partners.findById(failing.getId()).orElseThrow().getLastNotifiedStatus());
    }

    private PartnerRenewalNotifier notifier(NotificationService notifications, List<Partner> visible) {
        PartnerRepository onlyOurPartners = mock(PartnerRepository.class);
        when(onlyOurPartners.findAllByRenewalOrder()).thenAnswer(call -> visible.stream()
                .map(partner -> partners.findById(partner.getId()).orElseThrow())
                .toList());
        when(onlyOurPartners.claimNotification(any(), any()))
                .thenAnswer(call -> partners.claimNotification(call.getArgument(0), call.getArgument(1)));
        PartnerMouProperties properties = new PartnerMouProperties(
                60, 30, Set.of(), Set.of("alerts-" + UUID.randomUUID() + "@test.local"));
        Clock clock = Clock.fixed(TODAY.atTime(7, 0).atZone(KAMPALA).toInstant(), KAMPALA);
        return new PartnerRenewalNotifier(
                onlyOurPartners,
                new PartnerStatusPolicy(properties, clock),
                properties,
                recipientQuery,
                notifications,
                transactionManager);
    }

    private Partner seed(String prefix, LocalDate renewalDate) {
        Partner partner = partners.saveAndFlush(new Partner(UUID.randomUUID(),
                prefix + " " + UUID.randomUUID().toString().substring(0, 8), PartnerType.FUNDER,
                renewalDate, null, null, null, TODAY.atTime(7, 0).atZone(KAMPALA).toInstant()));
        created.add(partner.getId());
        return partner;
    }
}
