package org.unipod.unipoderpbackend.repository.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StaffNameQueryTest {
    @Autowired
    private StaffNameQuery names;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void showsTheProfileNameAndNeverTheEmail() {
        String email = "agnes-" + UUID.randomUUID() + "@test.local";
        UUID user = account(email);
        profile(user, "  Agnes Nabirye ");

        assertEquals("Agnes Nabirye", names.displayName(email.toUpperCase(java.util.Locale.ROOT)));
    }

    @Test
    void fallsBackToStaffWithoutAProfile() {
        String email = "noprofile-" + UUID.randomUUID() + "@test.local";
        account(email);

        assertEquals("Staff", names.displayName(email));
        assertEquals("Staff", names.displayName("nobody-" + UUID.randomUUID() + "@test.local"));
    }

    private UUID account(String email) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.parse("2026-11-12T05:00:00Z"));
        jdbcTemplate.update("INSERT INTO api_users (id, email, password_hash, role, enabled, created_at, updated_at) "
                + "VALUES (?, ?, 'x', 'OBSERVER', TRUE, ?, ?)", id, email, now, now);
        return id;
    }

    private void profile(UUID user, String fullName) {
        Timestamp now = Timestamp.from(Instant.parse("2026-11-12T05:00:00Z"));
        jdbcTemplate.update("INSERT INTO user_profiles (user_id, account_kind, full_name, phone, rules_version, "
                + "rules_accepted_at, created_at, updated_at) VALUES (?, 'PUBLIC', ?, '0700000000', 'v1', ?, ?, ?)",
                user, fullName, now, now, now);
    }
}
