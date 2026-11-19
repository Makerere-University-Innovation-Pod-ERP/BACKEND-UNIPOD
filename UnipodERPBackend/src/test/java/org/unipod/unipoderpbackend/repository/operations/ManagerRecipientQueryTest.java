package org.unipod.unipoderpbackend.repository.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.service.operations.DepartmentSectorNames;

@SpringBootTest
@Transactional
class ManagerRecipientQueryTest {
    @Autowired
    private ManagerRecipientQuery recipients;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void coordinatorSectorNamesMatchAfterTrimmingLeadingAndTrailingWhitespace() {
        UUID sectorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String email = "coordinator-" + userId + "@test.local";
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO sectors (id, name, notification_email, active, created_at, updated_at, version) "
                        + "VALUES (?, ?, ?, TRUE, ?, ?, 0)",
                sectorId, "\tTextile\n", "sector-" + sectorId + "@example.invalid", now, now);
        jdbc.update("INSERT INTO api_users (id, email, password_hash, role, enabled, created_at, updated_at) "
                        + "VALUES (?, ?, 'not-a-real-hash', 'DEPARTMENT_COORDINATOR', TRUE, ?, ?)",
                userId, email, now, now);
        jdbc.update("INSERT INTO sector_members (id, sector_id, subject) VALUES (?, ?, ?)",
                UUID.randomUUID(), sectorId, email);

        assertEquals(List.of(email), recipients.coordinatorEmails(DepartmentSectorNames.of(DepartmentCode.TEXTILE)));
    }
}
