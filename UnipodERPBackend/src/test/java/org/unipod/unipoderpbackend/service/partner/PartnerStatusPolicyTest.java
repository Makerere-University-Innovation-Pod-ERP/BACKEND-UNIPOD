package org.unipod.unipoderpbackend.service.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;

class PartnerStatusPolicyTest {
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    private final PartnerStatusPolicy policy = policyAt(Instant.parse("2026-11-11T09:00:00Z"));

    @ParameterizedTest(name = "{0} days left is {1}")
    @CsvSource({
            "61, ACTIVE",
            "60, RENEWAL_DUE",
            "31, RENEWAL_DUE",
            "30, EXPIRING_SOON",
            "1, EXPIRING_SOON",
            "0, EXPIRING_SOON",
            "-1, EXPIRED",
            "-400, EXPIRED"
    })
    void derivesStatusFromDaysUntilRenewal(long daysLeft, PartnerStatus expected) {
        assertEquals(expected, policy.statusOf(TODAY.plusDays(daysLeft)));
    }

    @Test
    void partnerWithoutRenewalDateIsActive() {
        assertEquals(PartnerStatus.ACTIVE, policy.statusOf(null));
    }

    @Test
    void usesTheKampalaCalendarDayNotUtc() {
        // 22:00 UTC on 11 Nov is already 12 Nov in Kampala, so an MOU due on 11 Nov has expired.
        PartnerStatusPolicy lateEvening = policyAt(Instant.parse("2026-11-11T22:00:00Z"));

        assertEquals(PartnerStatus.EXPIRED, lateEvening.statusOf(LocalDate.of(2026, 11, 11)));
        assertEquals(PartnerStatus.EXPIRING_SOON, lateEvening.statusOf(LocalDate.of(2026, 11, 12)));
    }

    @Test
    void respectsConfiguredThresholds() {
        PartnerStatusPolicy custom = new PartnerStatusPolicy(
                new PartnerMouProperties(14, 7), Clock.fixed(Instant.parse("2026-11-11T09:00:00Z"), KAMPALA));

        assertEquals(PartnerStatus.ACTIVE, custom.statusOf(TODAY.plusDays(15)));
        assertEquals(PartnerStatus.RENEWAL_DUE, custom.statusOf(TODAY.plusDays(14)));
        assertEquals(PartnerStatus.EXPIRING_SOON, custom.statusOf(TODAY.plusDays(7)));
    }

    @Test
    void thresholdsDefaultToSixtyAndThirtyDays() {
        PartnerMouProperties bound = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("operations.partners", PartnerMouProperties.class);

        assertEquals(60, bound.renewalDueDays());
        assertEquals(30, bound.expiringSoonDays());
    }

    @Test
    void thresholdsBindFromConfiguration() {
        PartnerMouProperties bound = new Binder(new MapConfigurationPropertySource(Map.of(
                "operations.partners.renewal-due-days", "90",
                "operations.partners.expiring-soon-days", "45")))
                .bindOrCreate("operations.partners", PartnerMouProperties.class);

        assertEquals(90, bound.renewalDueDays());
        assertEquals(45, bound.expiringSoonDays());
    }

    @Test
    void rejectsThresholdsInTheWrongOrder() {
        assertThrows(IllegalArgumentException.class, () -> new PartnerMouProperties(10, 20));
        assertThrows(IllegalArgumentException.class, () -> new PartnerMouProperties(10, -1));
    }

    private static PartnerStatusPolicy policyAt(Instant instant) {
        return new PartnerStatusPolicy(new PartnerMouProperties(60, 30), Clock.fixed(instant, KAMPALA));
    }
}
