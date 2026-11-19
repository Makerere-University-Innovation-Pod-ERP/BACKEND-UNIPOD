package org.unipod.unipoderpbackend.repository.stock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only lookup of a department order's code, so a textile movement can show the order it was used on. It reads
 * the {@code department_orders} table by column name only and imports none of that feature's classes.
 */
@Repository
public class OrderReferenceQuery {
    private final JdbcTemplate jdbc;

    public OrderReferenceQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The order's code, when {@code reference} is the id of an order of {@code department}. */
    public Optional<OrderRef> find(String department, String reference) {
        UUID id;
        try {
            id = UUID.fromString(reference.trim());
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
        List<OrderRef> found = jdbc.query("SELECT id, code FROM department_orders WHERE id = ? AND department = ?",
                (rs, row) -> new OrderRef(rs.getObject("id", UUID.class), rs.getString("code")), id, department);
        return found.stream().findFirst();
    }

    public record OrderRef(UUID id, String code) {
    }
}
