package org.unipod.unipoderpbackend.repository.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class PartnersSchemaTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storesPartnerWithOnlyRequiredColumns() {
        insert("Schema Test Partner", "FUNDER", null);

        Long version = jdbcTemplate.queryForObject(
                "SELECT version FROM partners WHERE name = ?", Long.class, "Schema Test Partner");
        assertEquals(0L, version);
    }

    @Test
    void rejectsDuplicateNameIgnoringCase() {
        insert("Duplicate Name Ltd", "INDUSTRY_PARTNER", null);

        assertViolates("uq_partners_name",
                () -> insert("DUPLICATE NAME LTD", "FUNDER", null));
    }

    @Test
    void rejectsBlankName() {
        assertViolates("ck_partners_name",
                () -> insert("   ", "FUNDER", null));
    }

    @Test
    void rejectsUnknownType() {
        assertViolates("ck_partners_type",
                () -> insert("Unknown Type Ltd", "VENDOR", null));
    }

    @Test
    void rejectsUnknownLastNotifiedStatus() {
        assertViolates("ck_partners_last_notified_status",
                () -> insert("Unknown Status Ltd", "FUNDER", "LAPSED"));
    }

    private void assertViolates(String constraint, Runnable statement) {
        DataIntegrityViolationException failure =
                assertThrows(DataIntegrityViolationException.class, statement::run);
        assertTrue(failure.getMessage().contains(constraint), failure.getMessage());
    }

    private void insert(String name, String type, String lastNotifiedStatus) {
        Timestamp now = Timestamp.from(Instant.parse("2026-11-11T09:00:00Z"));
        jdbcTemplate.update(
                "INSERT INTO partners (id, name, type, last_notified_status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), name, type, lastNotifiedStatus, now, now);
    }
}
