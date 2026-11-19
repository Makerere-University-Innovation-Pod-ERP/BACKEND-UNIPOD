package org.unipod.unipoderpbackend.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Rows the department tests need from tables we do not own: sectors, facilities and finance invoices.
 * Plain SQL keeps the tests independent of those features' classes.
 */
public final class OperationsTestData {
    private static final Instant SEEDED_AT = Instant.parse("2026-11-12T05:00:00Z");

    private OperationsTestData() {
    }

    /** The active sector with this name, created when it does not exist yet (names are unique, ignoring case). */
    public static UUID sector(JdbcTemplate jdbcTemplate, String name) {
        List<UUID> existing = jdbcTemplate.query(
                "SELECT id FROM sectors WHERE lower(name) = lower(?)",
                (resultSet, row) -> resultSet.getObject("id", UUID.class), name);
        if (!existing.isEmpty()) {
            jdbcTemplate.update("UPDATE sectors SET active = TRUE WHERE id = ?", existing.get(0));
            return existing.get(0);
        }
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(SEEDED_AT);
        jdbcTemplate.update(
                "INSERT INTO sectors (id, name, notification_email, active, created_at, updated_at) "
                        + "VALUES (?, ?, 'dept@test.local', TRUE, ?, ?)", id, name, now, now);
        return id;
    }

    public static void member(JdbcTemplate jdbcTemplate, UUID sectorId, String subject) {
        jdbcTemplate.update(
                "INSERT INTO sector_members (id, sector_id, subject) VALUES (?, ?, ?) "
                        + "ON CONFLICT (sector_id, subject) DO NOTHING", UUID.randomUUID(), sectorId, subject);
    }

    /** An available facility belonging to the department, with a unique name. */
    public static UUID facility(JdbcTemplate jdbcTemplate, String department, int capacity) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(SEEDED_AT);
        jdbcTemplate.update(
                "INSERT INTO facilities (id, name, capacity, status, department, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'AVAILABLE', ?, 'seed@test.local', ?, ?)",
                id, department + " test facility " + id, capacity, department, now, now);
        return id;
    }

    /**
     * Like {@link #facility}, but first moves every other facility of that department out of the way, so the
     * department's one facility is ours. Only for tests that roll back their changes.
     */
    public static UUID exclusiveFacility(JdbcTemplate jdbcTemplate, String department, int capacity) {
        String prefix = department.toLowerCase(java.util.Locale.ROOT).substring(0, Math.min(7, department.length()));
        jdbcTemplate.update(
                "UPDATE facilities SET department = 'moved by test' WHERE lower(btrim(department)) LIKE ?",
                prefix + "%");
        return facility(jdbcTemplate, department, capacity);
    }

    /**
     * Moves the department's existing facilities out of the way for a test that cannot roll back, and returns
     * their ids with the department text to put back with {@link #restoreFacilities}.
     */
    public static java.util.Map<UUID, String> moveFacilitiesAside(JdbcTemplate jdbcTemplate, String department) {
        String prefix = department.toLowerCase(java.util.Locale.ROOT).substring(0, Math.min(7, department.length()));
        java.util.Map<UUID, String> moved = new java.util.LinkedHashMap<>();
        jdbcTemplate.query("SELECT id, department FROM facilities WHERE lower(btrim(department)) LIKE ?",
                resultSet -> {
                    moved.put(resultSet.getObject("id", UUID.class), resultSet.getString("department"));
                }, prefix + "%");
        moved.keySet().forEach(id -> jdbcTemplate.update(
                "UPDATE facilities SET department = 'moved by test' WHERE id = ?", id));
        return moved;
    }

    public static void restoreFacilities(JdbcTemplate jdbcTemplate, java.util.Map<UUID, String> moved) {
        moved.forEach((id, department) -> jdbcTemplate.update(
                "UPDATE facilities SET department = ? WHERE id = ?", department, id));
    }

    /** A booking row in any stored status. */
    public static UUID booking(JdbcTemplate jdbcTemplate, UUID facilityId, Instant startsAt, Instant endsAt,
                               String status, int attendees, String purpose) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(SEEDED_AT);
        jdbcTemplate.update(
                "INSERT INTO bookings (id, reference, facility_id, requester_id, purpose, starts_at, ends_at, "
                        + "attendees, status, created_at, updated_at) VALUES (?, ?, ?, 'seed@test.local', ?, ?, ?, ?, ?, ?, ?)",
                id, "T-" + id.toString().replace("-", "").substring(0, 12), facilityId, purpose,
                Timestamp.from(startsAt), Timestamp.from(endsAt), attendees, status, now, now);
        return id;
    }

    /** The department detail row that makes a booking show up in a department screen. */
    public static void bookingDetail(JdbcTemplate jdbcTemplate, UUID bookingId, String department, String kind,
                                     String bookedBy, String customerType, String machineId, Integer capacity) {
        jdbcTemplate.update(
                "INSERT INTO department_booking_details (booking_id, department, kind, booked_by, customer_type, "
                        + "machine_id, capacity, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                bookingId, department, kind, bookedBy, customerType, machineId, capacity, Timestamp.from(SEEDED_AT));
    }

    public static UUID timetableBlock(JdbcTemplate jdbcTemplate, UUID facilityId, String title, Instant startsAt,
                                      Instant endsAt) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(SEEDED_AT);
        jdbcTemplate.update(
                "INSERT INTO timetable_blocks (id, facility_id, title, starts_at, ends_at, created_by, created_at, "
                        + "updated_at) VALUES (?, ?, ?, ?, ?, 'seed@test.local', ?, ?)",
                id, facilityId, title, Timestamp.from(startsAt), Timestamp.from(endsAt), now, now);
        return id;
    }

    public static UUID invoice(JdbcTemplate jdbcTemplate, String sourceType, String sourceId, String customerEmail,
                               String status, Instant createdAt) {
        UUID id = UUID.randomUUID();
        Timestamp created = Timestamp.from(createdAt);
        jdbcTemplate.update(
                "INSERT INTO invoices (id, number, customer_email, description, amount, source_type, source_id, "
                        + "status, issued_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'Test invoice', 50000, ?, ?, ?, 'finance@test.local', ?, ?)",
                id, "T" + id.toString().replace("-", "").substring(0, 20), customerEmail, sourceType, sourceId,
                status, created, created);
        return id;
    }

    public static void proof(JdbcTemplate jdbcTemplate, UUID invoiceId, String status, Instant uploadedAt) {
        jdbcTemplate.update(
                "INSERT INTO invoice_payment_proofs (id, invoice_id, reference, amount, paid_on, file_reference, "
                        + "content_type, file_size, status, uploaded_by, uploaded_at) "
                        + "VALUES (?, ?, 'REF-1', 50000, CURRENT_DATE, 'file-ref', 'application/pdf', 10, ?, "
                        + "'payer@test.local', ?)",
                UUID.randomUUID(), invoiceId, status, Timestamp.from(uploadedAt));
    }
}
