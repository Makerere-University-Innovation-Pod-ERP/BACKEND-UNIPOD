package org.unipod.unipoderpbackend.controller.media;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Media request rows for the board tests, inserted with SQL so any stored status can be seeded. */
final class MediaBoardTestData {
    static final String[] READ_ROLES = {
            "ADMINISTRATOR", "DEPARTMENT_COORDINATOR", "INNOVATION_COORDINATOR", "RECEPTIONIST",
            "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER"
    };
    // Every role that must be refused on a MANAGE endpoint.
    static final String[] NON_MANAGE_ROLES = {
            "INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER",
            "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER", "SUBMITTER"
    };

    private MediaBoardTestData() {
    }

    static UUID request(JdbcTemplate jdbcTemplate, String title, String status, Instant updatedAt) {
        return request(jdbcTemplate, title, status, updatedAt, null, null, null, null);
    }

    static UUID request(JdbcTemplate jdbcTemplate, String title, String status, Instant updatedAt,
                        UUID studioId, String assignee, Instant scheduledAt, String description) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO media_requests (id, code, submitter_id, title, status, created_at, updated_at, "
                        + "studio_id, assignee, scheduled_at, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, "MRQ-T-" + id.toString().substring(0, 8), "owner@test.local", title, status,
                Timestamp.from(updatedAt), Timestamp.from(updatedAt), studioId, assignee,
                scheduledAt == null ? null : Timestamp.from(scheduledAt), description);
        return id;
    }
}
