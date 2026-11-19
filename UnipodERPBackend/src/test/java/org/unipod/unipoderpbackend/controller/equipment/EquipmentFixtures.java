package org.unipod.unipoderpbackend.controller.equipment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Test data for the equipment, machine, loan and stock endpoints: users with a role, coordinators who belong to a
 * department (a sector named like it, D-046), and rows for the register tables. Everything is inserted with plain
 * SQL so the tests do not depend on the code they check.
 */
public final class EquipmentFixtures {
    public static final String[] ROLES_WITHOUT_ACCESS = {"SUBMITTER"};
    public static final String[] READ_ONLY_ROLES = {"OBSERVER"};
    public static final String[] ROLES_THAT_CAN_REPORT = {"INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER",
            "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER"};
    public static final String[] ROLES_THAT_CANNOT_MANAGE = {"INNOVATION_COORDINATOR", "RECEPTIONIST",
            "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER", "SUBMITTER"};
    public static final String[] ROLES_THAT_CANNOT_REPORT = {"OBSERVER", "SUBMITTER"};
    public static final String[] ROLES_THAT_CANNOT_READ = {"SUBMITTER"};

    private final JdbcTemplate jdbc;

    public EquipmentFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String email(String label) {
        return label + "-" + UUID.randomUUID() + "@test.local";
    }

    public static RequestPostProcessor as(String role) {
        return user(email(role.toLowerCase())).roles(role);
    }

    public static RequestPostProcessor as(String email, String role) {
        return user(email).roles(role);
    }

    /** A coordinator who belongs to the sector named like {@code department} (created if missing). */
    public RequestPostProcessor coordinatorOf(String department) {
        return as(coordinatorEmail(department), "DEPARTMENT_COORDINATOR");
    }

    public String coordinatorEmail(String department) {
        String email = email("coordinator");
        addMember(sector(department), email);
        return email;
    }

    public UUID sector(String name) {
        List<UUID> existing = jdbc.query("SELECT id FROM sectors WHERE lower(name) = lower(?)",
                (rs, row) -> rs.getObject(1, UUID.class), name);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO sectors (id, name, notification_email, active, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, TRUE, ?, ?, 0)", id, name, "sector-" + id + "@example.invalid", now, now);
        return id;
    }

    public void addMember(UUID sectorId, String subject) {
        jdbc.update("INSERT INTO sector_members (id, sector_id, subject) VALUES (?, ?, ?)", UUID.randomUUID(), sectorId, subject);
    }

    /** An enabled account with this role, so notifications have someone to reach. */
    public String insertUser(String role) {
        String email = email(role.toLowerCase());
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO api_users (id, email, password_hash, role, enabled, created_at, updated_at) "
                + "VALUES (?, ?, 'not-a-real-hash', ?, TRUE, ?, ?)", UUID.randomUUID(), email, role, now, now);
        return email;
    }

    public UUID equipment(String department, String name, int total, int available, String state) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO equipment_items (id, department, name, category, asset_tag, total_quantity, "
                        + "available_quantity, state, location, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Test', ?, ?, ?, ?, 'Bench', ?, ?)",
                id, department, name, "TAG-" + id, total, available, state, now, now);
        return id;
    }

    public UUID machine(String department, String name, String state) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO machines (id, department, name, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, department, name + " " + id, state, now, now);
        return id;
    }

    public UUID stockItem(String department, String name, String unit, String quantity, String minimum) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO stock_items (id, sku, name, department, quantity_on_hand, unit, minimum, location, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?::numeric, ?, ?::numeric, 'Store', ?)",
                id, "SKU-" + id, name, department, quantity, unit, minimum, now);
        return id;
    }

    public Map<String, Object> equipmentRow(UUID id) {
        return jdbc.queryForMap("SELECT * FROM equipment_items WHERE id = ?", id);
    }

    public List<Map<String, Object>> auditEntries(String actor, String action) {
        return jdbc.queryForList("SELECT entity_type, entity_id, actor_role, before_value::text AS before_text, "
                + "after_value::text AS after_text FROM audit_log WHERE actor = ? AND action = ? ORDER BY occurred_at", actor, action);
    }

    public long auditCount(String actor) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE actor = ?", Long.class, actor);
        return count == null ? 0 : count;
    }

    public long notificationsFor(String recipient, String type) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient = ? AND type = ?",
                Long.class, recipient, type);
        return count == null ? 0 : count;
    }
}
