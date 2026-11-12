package org.unipod.unipoderpbackend.repository.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerType;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class PartnerRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-11T09:00:00Z");

    @Autowired
    private PartnerRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void ordersByRenewalDateThenNameWithUndatedLast() {
        Partner undatedZeta = save("Zeta Undated", null);
        Partner undatedAlpha = save("alpha Undated", null);
        Partner late = save("Late Renewal", LocalDate.of(2027, 6, 1));
        Partner soonB = save("bravo Soon", LocalDate.of(2026, 12, 1));
        Partner soonA = save("Alpha Soon", LocalDate.of(2026, 12, 1));
        Partner expired = save("Expired Co", LocalDate.of(2026, 1, 15));
        entityManager.flush();
        entityManager.clear();

        List<UUID> expectedOrder = List.of(expired, soonA, soonB, late, undatedAlpha, undatedZeta).stream()
                .map(Partner::getId).toList();
        List<UUID> actualOrder = repository.findAllByRenewalOrder().stream()
                .map(Partner::getId).filter(expectedOrder::contains).toList();

        assertEquals(expectedOrder, actualOrder);
    }

    @Test
    void persistsAllPartnerFields() {
        Partner partner = new Partner(UUID.randomUUID(), "Round Trip Ltd", PartnerType.FUNDER,
                LocalDate.of(2027, 2, 3), "Jane Doe", "jane@roundtrip.test", "Some notes", NOW);
        partner.renewMou(LocalDate.of(2027, 2, 3), "MOU-77", NOW);
        entityManager.persistAndFlush(partner);
        entityManager.clear();

        Partner loaded = repository.findById(partner.getId()).orElseThrow();

        assertEquals("Round Trip Ltd", loaded.getName());
        assertEquals(PartnerType.FUNDER, loaded.getType());
        assertEquals(LocalDate.of(2027, 2, 3), loaded.getRenewalDate());
        assertEquals("Jane Doe", loaded.getContactPerson());
        assertEquals("jane@roundtrip.test", loaded.getContactEmail());
        assertEquals("Some notes", loaded.getNotes());
        assertEquals("MOU-77", loaded.getDocumentReference());
        assertEquals(NOW, loaded.getCreatedAt());
        assertEquals(0L, loaded.getVersion());
        assertNull(loaded.getLastNotifiedStatus());
    }

    @Test
    void rejectsDuplicateNameIgnoringCase() {
        save("Same Name Ltd", null);
        entityManager.flush();

        assertThrows(DataIntegrityViolationException.class, () -> {
            repository.saveAndFlush(partner("SAME NAME LTD", null));
        });
    }

    private Partner save(String name, LocalDate renewalDate) {
        return entityManager.persist(partner(name, renewalDate));
    }

    private static Partner partner(String name, LocalDate renewalDate) {
        return new Partner(UUID.randomUUID(), name, PartnerType.INDUSTRY_PARTNER, renewalDate, null, null, null, NOW);
    }
}
