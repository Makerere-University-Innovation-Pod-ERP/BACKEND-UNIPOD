package org.unipod.unipoderpbackend.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Every rule the database enforces for equipment, machines, reports, loans and stock (migration V50). */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class EquipmentSchemaTest {
    private static final Timestamp NOW = Timestamp.from(Instant.parse("2026-11-19T09:00:00Z"));

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesEquipmentWithDefaults() {
        UUID id = insertEquipment("Schema Camera", tag(), 2, 2, "AVAILABLE");

        assertEquals("", jdbc.queryForObject("SELECT category FROM equipment_items WHERE id = ?", String.class, id));
        assertEquals(0L, jdbc.queryForObject("SELECT version FROM equipment_items WHERE id = ?", Long.class, id));
    }

    @Test
    void rejectsAvailableAboveTotal() {
        assertViolates("ck_equipment_items_available", () -> insertEquipment("Too Many", tag(), 2, 3, "AVAILABLE"));
    }

    @Test
    void rejectsNegativeAvailable() {
        assertViolates("ck_equipment_items_available", () -> insertEquipment("Below Zero", tag(), 2, -1, "AVAILABLE"));
    }

    @Test
    void rejectsZeroTotal() {
        assertViolates("ck_equipment_items_total", () -> insertEquipment("No Units", tag(), 0, 0, "AVAILABLE"));
    }

    @Test
    void rejectsUnknownState() {
        assertViolates("ck_equipment_items_state", () -> insertEquipment("Odd State", tag(), 1, 1, "BROKEN"));
    }

    @Test
    void rejectsBlankName() {
        assertViolates("ck_equipment_items_name", () -> insertEquipment("  ", tag(), 1, 1, "AVAILABLE"));
    }

    @Test
    void rejectsDuplicateAssetTagIgnoringCase() {
        String tag = tag();
        insertEquipment("First", tag, 1, 1, "AVAILABLE");

        assertViolates("uq_equipment_items_asset_tag",
                () -> insertEquipment("Second", tag.toLowerCase(), 1, 1, "AVAILABLE"));
    }

    @Test
    void rejectsUnknownDepartment() {
        assertViolates("ck_equipment_items_department", () -> insertEquipment("Odd Dept", tag(), 1, 1, "AVAILABLE", "KITCHEN"));
    }

    @Test
    void storesMachineAndRejectsDuplicateNameInOneDepartment() {
        String name = "Machine " + UUID.randomUUID();
        insertMachine(name, "TEXTILE", "WORKING");

        assertViolates("uq_machines_department_name", () -> insertMachine(name.toUpperCase(), "TEXTILE", "WORKING"));
        insertMachine(name, "CNC", "WORKING");
    }

    @Test
    void rejectsUnknownMachineState() {
        assertViolates("ck_machines_state", () -> insertMachine("Odd " + UUID.randomUUID(), "TEXTILE", "BROKEN"));
    }

    @Test
    void faultReportNeedsExactlyOneSubject() {
        UUID equipment = insertEquipment("Report Subject", tag(), 1, 1, "AVAILABLE");
        UUID machine = insertMachine("Report Machine " + UUID.randomUUID(), "TEXTILE", "WORKING");

        assertViolates("ck_fault_reports_subject", () -> insertReport("FAULT", null, null));
        assertViolates("ck_fault_reports_subject", () -> insertReport("FAULT", equipment, machine));
        insertReport("FAULT", equipment, null);
        insertReport("SERVICE", null, machine);
    }

    @Test
    void equipmentCanOnlyHaveFaultReports() {
        UUID equipment = insertEquipment("Fault Only", tag(), 1, 1, "AVAILABLE");

        assertViolates("ck_fault_reports_equipment_fault", () -> insertReport("SERVICE", equipment, null));
    }

    @Test
    void rejectsUnknownReportKindAndBlankDescription() {
        UUID machine = insertMachine("Kind Machine " + UUID.randomUUID(), "TEXTILE", "WORKING");

        assertViolates("ck_fault_reports_kind", () -> insertReport("INSPECTION", null, machine));
        assertViolates("ck_fault_reports_description", () -> jdbc.update(
                "INSERT INTO fault_reports (id, kind, machine_id, description, reported_by, reported_at) "
                        + "VALUES (?, 'FAULT', ?, '   ', 'a@test.local', ?)", UUID.randomUUID(), machine, NOW));
    }

    @Test
    void storesPendingLoanAndRejectsApprovedAsAStoredStatus() {
        UUID equipment = insertEquipment("Loan Subject", tag(), 3, 3, "AVAILABLE");

        insertLoan(equipment, 1, "PENDING_APPROVAL", null, null);
        assertViolates("ck_equipment_loans_status", () -> insertLoan(equipment, 1, "APPROVED", NOW, null));
        assertViolates("ck_equipment_loans_status", () -> insertLoan(equipment, 1, "OVERDUE", NOW, null));
    }

    @Test
    void loanDecisionAndReturnFieldsMustMatchTheStatus() {
        UUID equipment = insertEquipment("Loan Rules", tag(), 3, 3, "AVAILABLE");

        assertViolates("ck_equipment_loans_decision", () -> insertLoan(equipment, 1, "ACTIVE", null, null));
        assertViolates("ck_equipment_loans_decision", () -> insertLoan(equipment, 1, "PENDING_APPROVAL", NOW, null));
        assertViolates("ck_equipment_loans_return", () -> insertLoan(equipment, 1, "RETURNED", NOW, null));
        insertLoan(equipment, 1, "RETURNED", NOW, NOW);
    }

    @Test
    void rejectsLoanBackBeforeOutAndNonPositiveQuantity() {
        UUID equipment = insertEquipment("Loan Dates", tag(), 3, 3, "AVAILABLE");

        assertViolates("ck_equipment_loans_dates", () -> jdbc.update(
                "INSERT INTO equipment_loans (id, code, equipment_id, quantity, borrower_name, borrower_type, "
                        + "borrower_contact, date_out, date_back, requested_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, 'B', 'Student', 'c', ?, ?, 'a@test.local', ?, ?)",
                UUID.randomUUID(), loanCode(), equipment, Date.valueOf("2026-11-20"), Date.valueOf("2026-11-19"), NOW, NOW));
        assertViolates("ck_equipment_loans_quantity", () -> insertLoan(equipment, 0, "PENDING_APPROVAL", null, null));
    }

    @Test
    void stockQuantitiesAreFractionalButNeverNegative() {
        UUID item = insertStockItem("2.50");

        jdbc.update("UPDATE stock_items SET quantity_on_hand = 1.25 WHERE id = ?", item);
        assertEquals(0, new BigDecimal("1.25").compareTo(
                jdbc.queryForObject("SELECT quantity_on_hand FROM stock_items WHERE id = ?", BigDecimal.class, item)));
        assertViolates("ck_stock_items_qty", () -> jdbc.update(
                "UPDATE stock_items SET quantity_on_hand = -0.01 WHERE id = ?", item));
        assertViolates("ck_stock_items_minimum", () -> jdbc.update(
                "UPDATE stock_items SET minimum = -1 WHERE id = ?", item));
    }

    @Test
    void newStockMovementsNeedATypeAndBalance() {
        UUID item = insertStockItem("5");

        assertViolates("ck_stock_movements_recorded", () -> jdbc.update(
                "INSERT INTO stock_movements (id, stock_item_id, delta, created_by, created_at) "
                        + "VALUES (?, ?, 1, 'a@test.local', ?)", UUID.randomUUID(), item, NOW));
        assertViolates("ck_stock_movements_type", () -> insertMovement(item, "STOLEN", "1"));
        assertViolates("ck_stock_movements_delta", () -> insertMovement(item, "RECEIVED", "0"));
        insertMovement(item, "RECEIVED", "1.5");
    }

    private void insertMovement(UUID item, String type, String delta) {
        jdbc.update("INSERT INTO stock_movements (id, stock_item_id, delta, movement_type, balance_after, created_by, "
                        + "created_at) VALUES (?, ?, ?::numeric, ?, 6.5, 'a@test.local', ?)",
                UUID.randomUUID(), item, delta, type, NOW);
    }

    private UUID insertStockItem(String quantity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO stock_items (id, sku, name, department, quantity_on_hand, updated_at) "
                        + "VALUES (?, ?, 'Schema stock', 'TEXTILE', ?::numeric, ?)",
                id, "SCHEMA-" + id, quantity, NOW);
        return id;
    }

    private UUID insertEquipment(String name, String assetTag, int total, int available, String state) {
        return insertEquipment(name, assetTag, total, available, state, "TELECOMS");
    }

    private UUID insertEquipment(String name, String assetTag, int total, int available, String state, String department) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO equipment_items (id, department, name, asset_tag, total_quantity, available_quantity, "
                        + "state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, department, name, assetTag, total, available, state, NOW, NOW);
        return id;
    }

    private UUID insertMachine(String name, String department, String state) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO machines (id, department, name, state, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                id, department, name, state, NOW, NOW);
        return id;
    }

    private void insertReport(String kind, UUID equipment, UUID machine) {
        jdbc.update("INSERT INTO fault_reports (id, kind, equipment_id, machine_id, description, reported_by, reported_at) "
                        + "VALUES (?, ?, ?, ?, 'It stopped', 'a@test.local', ?)",
                UUID.randomUUID(), kind, equipment, machine, NOW);
    }

    private void insertLoan(UUID equipment, int quantity, String status, Timestamp decidedAt, Timestamp returnedAt) {
        jdbc.update("INSERT INTO equipment_loans (id, code, equipment_id, quantity, borrower_name, borrower_type, "
                        + "borrower_contact, date_out, date_back, status, requested_by, decided_by, decided_at, "
                        + "incoming_condition, returned_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'Borrower', 'Student', 'contact', ?, ?, ?, 'a@test.local', ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), loanCode(), equipment, quantity, Date.valueOf("2026-11-19"), Date.valueOf("2026-11-26"),
                status, decidedAt == null ? null : "m@test.local", decidedAt,
                returnedAt == null ? null : "Fine", returnedAt, NOW, NOW);
    }

    private static String loanCode() {
        return "SCHEMA-" + UUID.randomUUID().toString().substring(0, 12);
    }

    private static String tag() {
        return "TAG-" + UUID.randomUUID();
    }

    // A failed statement aborts the whole Postgres transaction, so each expected failure runs behind a savepoint
    // and the next statement of the same test can carry on.
    private void assertViolates(String constraint, Runnable statement) {
        jdbc.execute("SAVEPOINT before_violation");
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class, statement::run);
        jdbc.execute("ROLLBACK TO SAVEPOINT before_violation");
        assertTrue(failure.getMessage().contains(constraint), failure.getMessage());
    }
}
