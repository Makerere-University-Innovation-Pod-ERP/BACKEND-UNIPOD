package org.unipod.unipoderpbackend.repository.operations;

import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only lookup of who manages a department: every enabled administrator, and the enabled department
 * coordinators who belong to a sector with one of the department's names. Reads {@code api_users},
 * {@code sector_members} and {@code sectors} by column name only (same approach as D-020).
 */
@Repository
public class ManagerRecipientQuery {
    private final JdbcTemplate jdbc;

    public ManagerRecipientQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> administratorEmails() {
        return jdbc.queryForList("SELECT email FROM api_users WHERE enabled = TRUE AND role = 'ADMINISTRATOR'", String.class);
    }

    /** {@code sectorNames} must be normalised as {@code DepartmentSectorNames.normalise} does (lower case, single spaces). */
    public List<String> coordinatorEmails(Collection<String> sectorNames) {
        if (sectorNames.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", sectorNames.stream().map(name -> "?").toList());
        return jdbc.queryForList("SELECT DISTINCT u.email FROM api_users u "
                        + "JOIN sector_members m ON lower(m.subject) = lower(u.email) "
                        + "JOIN sectors s ON s.id = m.sector_id "
                        + "WHERE u.enabled = TRUE AND u.role = 'DEPARTMENT_COORDINATOR' AND s.active = TRUE "
                        + "AND regexp_replace(lower(btrim(s.name)), '[\\s_-]+', ' ', 'g') IN (" + placeholders + ")",
                String.class, sectorNames.toArray());
    }
}
