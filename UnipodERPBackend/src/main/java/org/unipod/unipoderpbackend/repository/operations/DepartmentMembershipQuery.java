package org.unipod.unipoderpbackend.repository.operations;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only lookup of sector names. A user belongs to sectors (CurrentActor loads the ids); the operations modules
 * treat a sector named like a department as that department (D-046). It reads the {@code sectors} table by column
 * name only and imports none of the sector feature's classes.
 */
@Repository
public class DepartmentMembershipQuery {
    private final JdbcTemplate jdbc;

    public DepartmentMembershipQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> activeSectorNames(Collection<UUID> sectorIds) {
        if (sectorIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", sectorIds.stream().map(id -> "?").toList());
        return jdbc.queryForList("SELECT name FROM sectors WHERE active = TRUE AND id IN (" + placeholders + ")",
                String.class, sectorIds.toArray());
    }
}
