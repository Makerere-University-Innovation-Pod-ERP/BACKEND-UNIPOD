package org.unipod.unipoderpbackend.repository.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The name a department screen shows for a signed-in user, read from the account profiles by column name
 * only (no auth classes imported). An email is never shown on a screen every read role can open.
 */
@Repository
public class StaffNameQuery {
    static final String FALLBACK = "Staff";

    private final JdbcTemplate jdbcTemplate;

    public StaffNameQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The profile's full name, or "Staff" when the user has no profile or a blank name. */
    public String displayName(String email) {
        return jdbcTemplate.query(
                "SELECT btrim(p.full_name) FROM api_users u JOIN user_profiles p ON p.user_id = u.id "
                        + "WHERE lower(u.email) = lower(?)",
                (resultSet, row) -> resultSet.getString(1), email).stream()
                .filter(name -> name != null && !name.isEmpty())
                .findFirst().orElse(FALLBACK);
    }
}
