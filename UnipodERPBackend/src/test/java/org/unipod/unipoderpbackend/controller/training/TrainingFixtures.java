package org.unipod.unipoderpbackend.controller.training;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unipod.unipoderpbackend.model.training.TrainingSession;

/** Seed data shared by the training endpoint tests. */
final class TrainingFixtures {
    static final String[] READ_ROLES = {
            "ADMINISTRATOR", "DEPARTMENT_COORDINATOR", "INNOVATION_COORDINATOR", "RECEPTIONIST",
            "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER"
    };
    // Every role that must be refused on a MANAGE endpoint.
    static final String[] NON_MANAGE_ROLES = {
            "INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER",
            "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER", "SUBMITTER"
    };

    // Creation timestamps are irrelevant to these tests, so seed rows use a fixed instant.
    static final Instant SEEDED_AT = Instant.parse("2026-11-12T05:00:00Z");

    private TrainingFixtures() {
    }

    static UUID department(JdbcTemplate jdbcTemplate) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(SEEDED_AT);
        jdbcTemplate.update(
                "INSERT INTO sectors (id, name, notification_email, active, created_at, updated_at) "
                        + "VALUES (?, ?, 'dept@test.local', TRUE, ?, ?)",
                id, "Training Test Dept " + id, now, now);
        return id;
    }

    static TrainingSession session(UUID departmentId, Instant startsAt, Instant endsAt, int capacity, int fee,
                                   boolean published) {
        return new TrainingSession(
                UUID.randomUUID(), departmentId, "Laser cutting " + UUID.randomUUID(), "Eng. Nsubuga",
                startsAt, endsAt, capacity, "Lab 2", "Basics", fee, false, published, SEEDED_AT);
    }
}
