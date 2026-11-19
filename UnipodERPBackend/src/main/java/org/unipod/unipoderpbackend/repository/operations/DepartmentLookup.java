package org.unipod.unipoderpbackend.repository.operations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

/**
 * Finds the sector and the facility that stand for one of our departments (D-016, D-041, D-047).
 * Sectors and facilities carry free-text names, so each department lists the spellings it answers to.
 * Read-only SQL keeps us out of the sector and booking features' repositories.
 */
@Repository
public class DepartmentLookup {
    private static final Map<DepartmentCode, List<String>> NAMES = Map.of(
            DepartmentCode.MULTIMEDIA, List.of("multimedia", "media"),
            DepartmentCode.TEXTILE, List.of("textile", "textiles"),
            DepartmentCode.CNC, List.of("cnc"),
            DepartmentCode.TELECOMS, List.of("telecoms", "telecom", "telecommunications", "telecom lab"));

    private final JdbcTemplate jdbcTemplate;

    public DepartmentLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The active sector standing for the department, if one exists. */
    public Optional<UUID> sectorId(DepartmentCode department) {
        List<String> names = NAMES.get(department);
        return jdbcTemplate.query(
                "SELECT id FROM sectors WHERE active AND btrim(regexp_replace(lower(name), '[\\s_-]+', ' ', 'g')) IN ("
                        + marks(names)
                        + ") ORDER BY name LIMIT 1",
                (resultSet, row) -> resultSet.getObject("id", UUID.class),
                names.toArray()).stream().findFirst();
    }

    /** Every active sector standing for the department (a department may be spelled more than one way). */
    public List<UUID> sectorIds(DepartmentCode department) {
        List<String> names = NAMES.get(department);
        return jdbcTemplate.query(
                "SELECT id FROM sectors WHERE active AND btrim(regexp_replace(lower(name), '[\\s_-]+', ' ', 'g')) IN ("
                        + marks(names) + ")",
                (resultSet, row) -> resultSet.getObject("id", UUID.class), names.toArray());
    }

    /** The department's bookable facility; with several, the first by name. */
    public Optional<UUID> facilityId(DepartmentCode department) {
        List<String> names = NAMES.get(department);
        return jdbcTemplate.query(
                "SELECT id FROM facilities WHERE btrim(regexp_replace(lower(department), '[\\s_-]+', ' ', 'g')) IN ("
                        + marks(names)
                        + ") ORDER BY name LIMIT 1",
                (resultSet, row) -> resultSet.getObject("id", UUID.class),
                names.toArray()).stream().findFirst();
    }

    private static String marks(List<String> names) {
        return String.join(",", names.stream().map(name -> "?").toList());
    }
}
