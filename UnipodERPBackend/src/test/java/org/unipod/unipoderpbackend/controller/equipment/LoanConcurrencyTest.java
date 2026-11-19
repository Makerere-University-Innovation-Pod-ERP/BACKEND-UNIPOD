package org.unipod.unipoderpbackend.controller.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Not transactional: the database, not the test transaction, must decide who gets the last unit. Rows are removed
 * afterwards; audit entries stay because the audit log is append-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoanConcurrencyTest {
    private static final String PATH = "/telecoms/equipment/loans";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> equipment = new ArrayList<>();

    @AfterEach
    void removeRows() {
        for (UUID id : equipment) {
            jdbc.update("DELETE FROM equipment_loans WHERE equipment_id = ?", id);
            jdbc.update("DELETE FROM fault_reports WHERE equipment_id = ?", id);
            jdbc.update("DELETE FROM equipment_items WHERE id = ?", id);
        }
    }

    @Test
    void twoApprovalsForTheLastUnitLetExactlyOneWin() throws Exception {
        UUID item = item(1, 1, "AVAILABLE");
        String first = pendingLoan(item, 1);
        String second = pendingLoan(item, 1);

        List<Integer> statuses = ConcurrentRunner.runTogether(List.of(approve(first), approve(second)));

        assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        assertEquals(0, availableOf(item));
        assertEquals("LOANED", stateOf(item));
        assertEquals(1, countWithStatus(item, "ACTIVE"));
        assertEquals(1, countWithStatus(item, "PENDING_APPROVAL"));
    }

    @Test
    void manyApprovalsNeverReserveMoreThanTheTotal() throws Exception {
        UUID item = item(3, 3, "AVAILABLE");
        List<Callable<Integer>> approvals = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            approvals.add(approve(pendingLoan(item, 1)));
        }

        List<Integer> statuses = ConcurrentRunner.runTogether(approvals);

        assertEquals(3, statuses.stream().filter(status -> status == 200).count());
        assertEquals(3, statuses.stream().filter(status -> status == 409).count());
        assertEquals(0, availableOf(item));
        assertEquals(3, countWithStatus(item, "ACTIVE"));
    }

    @Test
    void anApprovalRacingAReturnLeavesTheCountsConsistent() throws Exception {
        UUID item = item(1, 1, "AVAILABLE");
        String out = pendingLoan(item, 1);
        assertEquals(200, call(approve(out)));
        String waiting = pendingLoan(item, 1);

        List<Integer> statuses = ConcurrentRunner.runTogether(List.of(giveBack(out), approve(waiting)));

        assertEquals(200, statuses.get(0));
        // Whichever order the database chose, the units on the shelf plus the units out always equal the total.
        int activeUnits = countWithStatus(item, "ACTIVE");
        assertEquals(1 - activeUnits, availableOf(item));
        assertTrue(statuses.get(1) == 200 || statuses.get(1) == 409, statuses.toString());
        assertEquals(statuses.get(1) == 200 ? 1 : 0, activeUnits);
    }

    @Test
    void aFaultRacingAnApprovalNeverLendsFaultyEquipment() throws Exception {
        UUID item = item(2, 2, "AVAILABLE");
        String loan = pendingLoan(item, 1);

        List<Integer> statuses = ConcurrentRunner.runTogether(List.of(approve(loan), reportFault(item)));

        assertEquals(200, statuses.get(1));
        assertEquals("FAULTY", stateOf(item));
        if (statuses.get(0) == 200) {
            assertEquals(1, availableOf(item));
            assertEquals(1, countWithStatus(item, "ACTIVE"));
        } else {
            assertEquals(409, statuses.get(0));
            assertEquals(2, availableOf(item));
            assertEquals(0, countWithStatus(item, "ACTIVE"));
        }
    }

    private Callable<Integer> approve(String code) {
        return () -> mockMvc.perform(MockMvcRequestBuilders.post(PATH + "/{id}/approve", code)
                        .with(user("admin-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR")))
                .andReturn().getResponse().getStatus();
    }

    private Callable<Integer> giveBack(String code) {
        return () -> mockMvc.perform(MockMvcRequestBuilders.post(PATH + "/{id}/return", code)
                        .with(user("admin-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"Fine\"}"))
                .andReturn().getResponse().getStatus();
    }

    private Callable<Integer> reportFault(UUID item) {
        return () -> mockMvc.perform(MockMvcRequestBuilders.post("/telecoms/equipment/report-fault")
                        .with(user("admin-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\": \"%s\", \"description\": \"Dead\"}".formatted(item)))
                .andReturn().getResponse().getStatus();
    }

    private int call(Callable<Integer> request) throws Exception {
        return request.call();
    }

    private UUID item(int total, int available, String state) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO equipment_items (id, department, name, asset_tag, total_quantity, available_quantity, state, "
                        + "created_at, updated_at) VALUES (?, 'TELECOMS', 'Race item', ?, ?, ?, ?, ?, ?)",
                id, "RACE-" + id, total, available, state, now, now);
        equipment.add(id);
        return id;
    }

    /** Inserted directly so the race is about approving, not about requesting. */
    private String pendingLoan(UUID item, int quantity) {
        String code = "RACE-" + UUID.randomUUID().toString().substring(0, 12);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO equipment_loans (id, code, equipment_id, quantity, borrower_name, borrower_type, "
                        + "borrower_contact, date_out, date_back, requested_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'Racer', 'Student', 'contact', ?, ?, 'a@test.local', ?, ?)",
                UUID.randomUUID(), code, item, quantity, Date.valueOf(LocalDate.now()), Date.valueOf(LocalDate.now().plusDays(7)),
                now, now);
        return code;
    }

    private int availableOf(UUID item) {
        return jdbc.queryForObject("SELECT available_quantity FROM equipment_items WHERE id = ?", Integer.class, item);
    }

    private String stateOf(UUID item) {
        return jdbc.queryForObject("SELECT state FROM equipment_items WHERE id = ?", String.class, item);
    }

    private int countWithStatus(UUID item, String status) {
        return jdbc.queryForObject("SELECT coalesce(sum(quantity), 0) FROM equipment_loans WHERE equipment_id = ? AND status = ?",
                Integer.class, item, status);
    }
}
