package org.unipod.unipoderpbackend.service.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.unipod.unipoderpbackend.repository.partner.AlertRecipientQuery;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

/**
 * The "which status has been announced" marker is shared between the daily job and people editing
 * partners. Not transactional, so every step really commits and the races are real.
 */
@SpringBootTest
class PartnerNotificationStateTest {
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final Instant MORNING = TODAY.atTime(7, 0).atZone(KAMPALA).toInstant();

    @Autowired
    private PartnerRepository partners;

    @Autowired
    private AlertRecipientQuery recipientQuery;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> created = new ArrayList<>();
    private final String recipient = "state-" + UUID.randomUUID() + "@test.local";

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM notifications WHERE recipient = ?", recipient);
        partners.deleteAllById(created);
    }

    @Test
    void anEditDoesNotUndoAnAnnouncementMadeAfterItLoadedThePartner() {
        Partner partner = seed("Edited", TODAY.plusDays(45));
        TransactionTemplate edit = new TransactionTemplate(transactionManager);
        TransactionTemplate job = new TransactionTemplate(transactionManager);
        job.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        edit.executeWithoutResult(status -> {
            Partner loaded = partners.findById(partner.getId()).orElseThrow();
            // The daily job announces the partner while this edit is still open.
            job.executeWithoutResult(inner ->
                    partners.claimNotification(partner.getId(), PartnerStatus.RENEWAL_DUE, partner.getRenewalDate()));
            loaded.updateDetails(null, null, null, null, null, "edited meanwhile", MORNING.plusSeconds(5));
            partners.saveAndFlush(loaded);
        });

        Partner stored = partners.findById(partner.getId()).orElseThrow();
        assertEquals("edited meanwhile", stored.getNotes());
        assertEquals(PartnerStatus.RENEWAL_DUE, stored.getLastNotifiedStatus());
    }

    @Test
    void aClaimBasedOnASupersededRenewalDateIsRefused() {
        Partner partner = seed("Superseded", TODAY.plusDays(45));

        assertEquals(0, partners.claimNotification(
                partner.getId(), PartnerStatus.RENEWAL_DUE, partner.getRenewalDate().plusDays(1)));
        assertNull(partners.findById(partner.getId()).orElseThrow().getLastNotifiedStatus());
        assertEquals(1, partners.claimNotification(
                partner.getId(), PartnerStatus.RENEWAL_DUE, partner.getRenewalDate()));
    }

    @Test
    void aNewRenewalDateForgetsWhatWasAnnouncedForTheOldOne() {
        Partner renewed = seed("Renewed", TODAY.plusDays(20));
        Partner edited = seed("Rescheduled", TODAY.plusDays(20));
        Partner sameDate = seed("Same date", TODAY.plusDays(20));
        for (Partner partner : List.of(renewed, edited, sameDate)) {
            partners.claimNotification(partner.getId(), PartnerStatus.EXPIRING_SOON, partner.getRenewalDate());
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            partners.findById(renewed.getId()).orElseThrow().renewMou(TODAY.plusDays(25), "MOU-2", MORNING);
            partners.findById(edited.getId()).orElseThrow()
                    .updateDetails(null, null, TODAY.plusDays(26), null, null, null, MORNING);
            partners.findById(sameDate.getId()).orElseThrow()
                    .updateDetails(null, null, TODAY.plusDays(20), null, null, "other change", MORNING);
        });

        assertNull(partners.findById(renewed.getId()).orElseThrow().getLastNotifiedStatus());
        assertNull(partners.findById(edited.getId()).orElseThrow().getLastNotifiedStatus());
        assertEquals(PartnerStatus.EXPIRING_SOON,
                partners.findById(sameDate.getId()).orElseThrow().getLastNotifiedStatus());
    }

    @Test
    void twoInstancesRunningTheJobAtTheSameMomentAnnounceOnce() throws Exception {
        Partner partner = seed("Raced", TODAY.plusDays(45));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> runs = new ArrayList<>();
            for (int instance = 0; instance < 2; instance++) {
                PartnerRenewalNotifier notifier = notifierSeeing(partner);
                Callable<Integer> run = () -> {
                    ready.countDown();
                    go.await();
                    return notifier.notifyDueRenewals();
                };
                runs.add(pool.submit(run));
            }
            ready.await();
            go.countDown();
            int announced = 0;
            for (Future<Integer> run : runs) {
                announced += run.get(30, TimeUnit.SECONDS);
            }

            assertEquals(1, announced);
        } finally {
            pool.shutdownNow();
        }
        Long rows = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient = ? AND entity_id = ?",
                Long.class, recipient, partner.getId());
        assertEquals(1L, rows);
        assertEquals(PartnerStatus.RENEWAL_DUE,
                partners.findById(partner.getId()).orElseThrow().getLastNotifiedStatus());
    }

    // Each instance reads the partner as it was before either announced it, like two servers would.
    private PartnerRenewalNotifier notifierSeeing(Partner partner) {
        PartnerRepository view = mock(PartnerRepository.class);
        when(view.findAllByRenewalOrder()).thenReturn(List.of(partner));
        when(view.claimNotification(any(), any(), any())).thenAnswer(call -> partners.claimNotification(
                call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        PartnerMouProperties properties = new PartnerMouProperties(60, 30, Set.of(), Set.of(recipient));
        Clock clock = Clock.fixed(MORNING, KAMPALA);
        return new PartnerRenewalNotifier(
                view, new PartnerStatusPolicy(properties, clock), properties, recipientQuery, notifications,
                transactionManager);
    }

    private Partner seed(String prefix, LocalDate renewalDate) {
        Partner partner = partners.saveAndFlush(new Partner(UUID.randomUUID(),
                prefix + " " + UUID.randomUUID().toString().substring(0, 8), PartnerType.FUNDER,
                renewalDate, null, null, null, MORNING));
        created.add(partner.getId());
        return partner;
    }
}
