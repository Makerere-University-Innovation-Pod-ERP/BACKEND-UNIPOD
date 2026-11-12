package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;

/** Seed data and audit checks shared by the training endpoint tests. */
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
    // The details of the seeded participant; none of them may ever appear in an audit line (D-027).
    static final List<String> PERSONAL_DETAILS = List.of("Amina", "Nakato", "amina@students.test", "Engineering");

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
        return session(departmentId, startsAt, endsAt, capacity, fee, published, false);
    }

    static TrainingSession session(UUID departmentId, Instant startsAt, Instant endsAt, int capacity, int fee,
                                   boolean published, boolean safetyCourse) {
        return new TrainingSession(
                UUID.randomUUID(), departmentId, "Laser cutting " + UUID.randomUUID(), "Eng. Nsubuga",
                startsAt, endsAt, capacity, "Lab 2", "Basics", fee, safetyCourse, published, SEEDED_AT);
    }

    static TrainingParticipant participant(UUID sessionId, String regNumber, int fee) {
        return new TrainingParticipant(
                UUID.randomUUID(), sessionId, "Amina Nakato", regNumber, "Engineering",
                "amina@students.test", fee, SEEDED_AT);
    }

    /** Every audit line written for one entity. Pending JPA inserts are flushed first so the raw query sees them. */
    static List<Map<String, Object>> auditLines(EntityManager entityManager, JdbcTemplate jdbcTemplate, UUID entityId) {
        entityManager.flush();
        return jdbcTemplate.queryForList(
                "SELECT actor, actor_role, action, entity_type, department_id, before_value::text AS before_value, "
                        + "after_value::text AS after_value FROM audit_log WHERE entity_id = ? "
                        + "ORDER BY occurred_at, id", entityId);
    }

    static void assertNoPersonalData(Map<String, Object> auditLine, String... alsoForbidden) {
        String text = auditLine.get("before_value") + " " + auditLine.get("after_value");
        for (String detail : PERSONAL_DETAILS) {
            assertFalse(text.contains(detail), "audit line leaks: " + detail);
        }
        for (String detail : alsoForbidden) {
            assertFalse(text.contains(detail), "audit line leaks: " + detail);
        }
    }
}
