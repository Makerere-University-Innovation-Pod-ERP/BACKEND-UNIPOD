package org.unipod.unipoderpbackend.service.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;
import org.unipod.unipoderpbackend.model.partner.PartnerType;
import org.unipod.unipoderpbackend.repository.partner.AlertRecipientQuery;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

/** The job is driven by a fixed clock; "days" are counted from the Kampala day of {@code TODAY}. */
@SpringBootTest
@Transactional
class PartnerRenewalNotifierTest {
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

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ScheduledAnnotationBeanPostProcessor scheduler;

    @Autowired
    private Environment environment;

    private final String recipient = "alerts-" + UUID.randomUUID() + "@test.local";

    @Test
    void announcesAPartnerOnceWhenItEntersRenewalDue() {
        Partner partner = seed("Due", TODAY.plusDays(45));

        assertTrue(runAt(TODAY) >= 1);
        assertEquals(PartnerStatus.RENEWAL_DUE, storedStatus(partner));
        assertEquals(1, notificationsFor(partner));

        runAt(TODAY);
        runAt(TODAY.plusDays(3));

        assertEquals(1, notificationsFor(partner));
    }

    @Test
    void announcesEachWorseningStatusOnceAsTimePasses() {
        Partner partner = seed("Worsening", TODAY.plusDays(45));

        runAt(TODAY);
        runAt(TODAY.plusDays(20));
        runAt(TODAY.plusDays(20));
        runAt(TODAY.plusDays(46));
        runAt(TODAY.plusDays(60));

        assertEquals(3, notificationsFor(partner));
        assertEquals(PartnerStatus.EXPIRED, storedStatus(partner));
        List<String> titles = jdbc.queryForList(
                "SELECT title FROM notifications WHERE entity_id = ? AND recipient = ? ORDER BY created_at, title",
                String.class, partner.getId(), recipient);
        assertTrue(titles.stream().anyMatch(title -> title.endsWith("is due for renewal")), titles.toString());
        assertTrue(titles.stream().anyMatch(title -> title.endsWith("expires soon")), titles.toString());
        assertTrue(titles.stream().anyMatch(title -> title.endsWith("has expired")), titles.toString());
    }

    @Test
    void doesNotAnnounceActiveOrUndatedPartners() {
        Partner active = seed("Active", TODAY.plusDays(200));
        Partner undated = seed("Undated", null);

        runAt(TODAY);

        assertEquals(0, notificationsFor(active));
        assertEquals(0, notificationsFor(undated));
        assertNull(storedStatus(active));
        assertNull(storedStatus(undated));
    }

    @Test
    void renewalResetsThePartnerSoTheNextWarningIsAnnouncedAgain() {
        Partner partner = seed("Renewed", TODAY.plusDays(20));
        runAt(TODAY);
        assertEquals(1, notificationsFor(partner));

        Partner reloaded = partners.findById(partner.getId()).orElseThrow();
        reloaded.renewMou(TODAY.plusDays(400), "MOU-NEXT", MORNING);
        partners.saveAndFlush(reloaded);
        // The new date has its own warnings, so the old announcement is forgotten straight away.
        assertNull(storedStatus(partner));
        runAt(TODAY);
        assertEquals(1, notificationsFor(partner));

        runAt(TODAY.plusDays(400 - 40));

        assertEquals(PartnerStatus.RENEWAL_DUE, storedStatus(partner));
        assertEquals(2, notificationsFor(partner));
    }

    @Test
    void expiredPartnersAreAnnounced() {
        Partner partner = seed("Expired", TODAY.minusDays(3));

        runAt(TODAY);

        assertEquals(PartnerStatus.EXPIRED, storedStatus(partner));
        String body = jdbc.queryForObject(
                "SELECT body FROM notifications WHERE entity_id = ? AND recipient = ?",
                String.class, partner.getId(), recipient);
        assertTrue(body.contains("expired on " + TODAY.minusDays(3)), body);
    }

    @Test
    void sendsNothingAndMarksNothingWhenThereAreNoRecipients() {
        Partner partner = seed("Nobody", TODAY.plusDays(10));
        PartnerMouProperties noRecipients = new PartnerMouProperties(60, 30, Set.of(), Set.of());

        int announced = notifier(TODAY, noRecipients).notifyDueRenewals();

        assertEquals(0, announced);
        assertEquals(0, notificationsFor(partner));
        assertNull(storedStatus(partner));

        runAt(TODAY);

        assertEquals(1, notificationsFor(partner));
    }

    @Test
    void alertsActiveUsersWithAConfiguredRoleOnly() {
        String admin = insertUser("admin", "ADMINISTRATOR", true);
        String disabledAdmin = insertUser("disabled", "ADMINISTRATOR", false);
        String observer = insertUser("observer", "OBSERVER", true);
        Partner partner = seed("Roles", TODAY.plusDays(10));
        PartnerMouProperties byRole = new PartnerMouProperties(60, 30, Set.of("administrator"), Set.of());

        notifier(TODAY, byRole).notifyDueRenewals();
        entityManager.flush();

        assertEquals(1, notificationsTo(admin, partner));
        assertEquals(0, notificationsTo(disabledAdmin, partner));
        assertEquals(0, notificationsTo(observer, partner));
    }

    @Test
    void roleNamesInSettingsAreNormalised() {
        PartnerMouProperties properties = new PartnerMouProperties(
                60, 30, Set.of(" administrator ", ""), Set.of(" Extra@Test.Local ", " "));

        assertEquals(Set.of("ADMINISTRATOR"), properties.alertRoles());
        assertEquals(Set.of("extra@test.local"), properties.alertRecipients());
    }

    @Test
    void recipientQueryIgnoresDisabledUsersAndOtherRoles() {
        String active = insertUser("active", "REVIEWER", true);
        insertUser("inactive", "REVIEWER", false);
        insertUser("other", "OBSERVER", true);

        List<String> emails = recipientQuery.activeEmailsWithRole(Set.of("REVIEWER"));

        assertTrue(emails.contains(active));
        assertEquals(1, emails.stream().filter(email -> email.contains("@alerts.test")).count());
        assertEquals(List.of(), recipientQuery.activeEmailsWithRole(Set.of()));
    }

    @Test
    void jobRunsDailyInKampalaTime() throws Exception {
        Method run = Class.forName("org.unipod.unipoderpbackend.service.partner.PartnerRenewalJob")
                .getDeclaredMethod("run");

        Scheduled scheduled = run.getAnnotation(Scheduled.class);

        assertEquals("Africa/Kampala", scheduled.zone());
        assertEquals("${operations.partners.alert-cron:0 0 7 * * *}", scheduled.cron());
    }

    @Test
    void theDailyJobIsRegisteredWithTheScheduler() {
        // Whatever the deployment sets for the schedule, the job must be registered with it.
        String expectedCron = environment.getProperty("operations.partners.alert-cron", "0 0 7 * * *");
        boolean registered = scheduler.getScheduledTasks().stream()
                .map(task -> task.getTask())
                .filter(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .anyMatch(cron -> cron.getRunnable().toString().contains("PartnerRenewalJob")
                        && expectedCron.equals(cron.getExpression()));

        assertTrue(registered, "PartnerRenewalJob is not registered with the scheduler.");
    }

    @Test
    void onlyOneCallerCanClaimAStatus() {
        Partner partner = seed("Claim", TODAY.plusDays(45));

        LocalDate date = partner.getRenewalDate();

        assertEquals(1, partners.claimNotification(partner.getId(), PartnerStatus.RENEWAL_DUE, date));
        assertEquals(0, partners.claimNotification(partner.getId(), PartnerStatus.RENEWAL_DUE, date));
        assertEquals(1, partners.claimNotification(partner.getId(), PartnerStatus.EXPIRING_SOON, date));
    }

    @Test
    void aSecondInstanceWorkingFromAStaleListDoesNotAnnounceAgain() {
        Partner partner = seed("Stale", TODAY.plusDays(45));
        List<Partner> staleSnapshot = partners.findAllByRenewalOrder().stream()
                .filter(candidate -> candidate.getId().equals(partner.getId()))
                .toList();
        PartnerRepository secondInstanceView = mock(PartnerRepository.class);
        when(secondInstanceView.findAllByRenewalOrder()).thenReturn(staleSnapshot);
        when(secondInstanceView.claimNotification(any(), any(), any())).thenAnswer(call -> partners.claimNotification(
                call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        PartnerMouProperties properties = new PartnerMouProperties(60, 30, Set.of(), Set.of(recipient));

        runAt(TODAY);
        new PartnerRenewalNotifier(
                secondInstanceView,
                new PartnerStatusPolicy(properties, Clock.fixed(MORNING, KAMPALA)),
                properties,
                recipientQuery,
                notifications,
                transactionManager).notifyDueRenewals();
        entityManager.flush();

        assertEquals(1, notificationsFor(partner));
    }

    /** Runs the job as of {@code day}, alerting only the test recipient; returns how many it announced. */
    private int runAt(LocalDate day) {
        PartnerMouProperties properties = new PartnerMouProperties(60, 30, Set.of(), Set.of(recipient));
        int announced = notifier(day, properties).notifyDueRenewals();
        entityManager.flush();
        return announced;
    }

    private PartnerRenewalNotifier notifier(LocalDate day, PartnerMouProperties properties) {
        Clock clock = Clock.fixed(day.atTime(7, 0).atZone(KAMPALA).toInstant(), KAMPALA);
        return new PartnerRenewalNotifier(
                partners,
                new PartnerStatusPolicy(properties, clock),
                properties,
                recipientQuery,
                notifications,
                transactionManager);
    }

    private Partner seed(String prefix, LocalDate renewalDate) {
        return partners.saveAndFlush(new Partner(UUID.randomUUID(),
                prefix + " " + UUID.randomUUID().toString().substring(0, 8), PartnerType.INDUSTRY_PARTNER,
                renewalDate, null, null, null, MORNING));
    }

    private PartnerStatus storedStatus(Partner partner) {
        entityManager.flush();
        entityManager.clear();
        return partners.findById(partner.getId()).orElseThrow().getLastNotifiedStatus();
    }

    private long notificationsFor(Partner partner) {
        return notificationsTo(recipient, partner);
    }

    private long notificationsTo(String email, Partner partner) {
        entityManager.flush();
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient = ? AND entity_id = ? AND type = 'AGREEMENT_EXPIRING'",
                Long.class, email, partner.getId());
        return count == null ? 0 : count;
    }

    private String insertUser(String label, String role, boolean enabled) {
        String email = label + "-" + UUID.randomUUID() + "@alerts.test";
        Timestamp now = Timestamp.from(MORNING);
        jdbc.update("INSERT INTO api_users (id, email, password_hash, role, enabled, created_at, updated_at) "
                        + "VALUES (?, ?, 'not-a-real-hash', ?, ?, ?, ?)",
                UUID.randomUUID(), email, role, enabled, now, now);
        return email;
    }
}
