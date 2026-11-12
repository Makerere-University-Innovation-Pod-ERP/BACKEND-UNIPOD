package org.unipod.unipoderpbackend.repository.partner;

import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only lookup of who should receive MOU alerts (D-020). It reads the auth feature's
 * {@code api_users} table by column name only and imports none of that feature's classes.
 */
@Repository
public class AlertRecipientQuery {
    private final JdbcTemplate jdbc;

    public AlertRecipientQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Emails of enabled users whose role is one of {@code roles}. */
    public List<String> activeEmailsWithRole(Collection<String> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", roles.stream().map(role -> "?").toList());
        return jdbc.queryForList(
                "SELECT email FROM api_users WHERE enabled = TRUE AND role IN (" + placeholders + ")",
                String.class,
                roles.toArray());
    }
}
