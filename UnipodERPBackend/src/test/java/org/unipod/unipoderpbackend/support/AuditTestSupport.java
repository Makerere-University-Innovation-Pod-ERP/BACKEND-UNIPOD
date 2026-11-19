package org.unipod.unipoderpbackend.support;

import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the audit lines a request wrote and checks that no personal data reached them (D-021). */
public final class AuditTestSupport {
    private AuditTestSupport() {
    }

    /** Every audit line written for one entity. Pending JPA inserts are flushed first so the raw query sees them. */
    public static List<Map<String, Object>> lines(EntityManager entityManager, JdbcTemplate jdbcTemplate, UUID entityId) {
        entityManager.flush();
        return jdbcTemplate.queryForList(
                "SELECT actor, actor_role, action, entity_type, department_id, before_value::text AS before_value, "
                        + "after_value::text AS after_value FROM audit_log WHERE entity_id = ? "
                        + "ORDER BY occurred_at, id", entityId);
    }

    public static List<Map<String, Object>> linesNotFlushed(JdbcTemplate jdbcTemplate, UUID entityId) {
        return jdbcTemplate.queryForList(
                "SELECT actor, actor_role, action, entity_type, before_value::text AS before_value, "
                        + "after_value::text AS after_value FROM audit_log WHERE entity_id = ? "
                        + "ORDER BY occurred_at, id", entityId);
    }

    public static void assertNoPersonalData(Map<String, Object> auditLine, String... forbidden) {
        String text = auditLine.get("before_value") + " " + auditLine.get("after_value");
        for (String detail : forbidden) {
            assertFalse(text.contains(detail), "audit line leaks: " + detail);
        }
    }
}
