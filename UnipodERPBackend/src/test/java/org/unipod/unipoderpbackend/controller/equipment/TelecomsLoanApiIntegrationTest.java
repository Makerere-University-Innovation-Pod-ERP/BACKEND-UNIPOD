package org.unipod.unipoderpbackend.controller.equipment;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TelecomsLoanApiIntegrationTest {
    private static final String PATH = "/telecoms/equipment/loans";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    // The requests write through JPA inside the test transaction; flush so the plain SQL checks can see the rows.
    @Autowired
    private EntityManager entityManager;

    private EquipmentFixtures fixtures() {
        return new EquipmentFixtures(jdbc);
    }

    // ---- request ---------------------------------------------------------------------------------------------

    @Test
    void requestingALoanWaitsForApprovalReservesNothingAndKeepsBorrowerDetailsOutOfTheAuditLog() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Oscilloscope", 3, 3, "AVAILABLE");
        String admin = fixtures.insertUser("ADMINISTRATOR");
        String actor = EquipmentFixtures.email("technician");

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as(actor, "FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON).content(request(equipment, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", matchesPattern("LOAN-\\d{6}")))
                .andExpect(jsonPath("$.equipmentId").value(equipment.toString()))
                .andExpect(jsonPath("$.equipmentName").value("Oscilloscope"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.borrowerName").value("Peter Okello (S24/1048)"))
                .andExpect(jsonPath("$.borrowerType").value("Student"))
                .andExpect(jsonPath("$.borrowerContact").value("0772123456"))
                .andExpect(jsonPath("$.dateOut").value(today().toString()))
                .andExpect(jsonPath("$.dateBack").value(today().plusDays(7).toString()))
                .andExpect(jsonPath("$.outgoingCondition").value("Calibrated and clean"))
                .andExpect(jsonPath("$.incomingCondition").value((Object) null))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.managerApproval").value((Object) null))
                .andExpect(jsonPath("$.createdAt").isString());

        entityManager.flush();
        assertEquals(3, fixtures.equipmentRow(equipment).get("available_quantity"));
        List<Map<String, Object>> entries = fixtures.auditEntries(actor, "equipment_loan.requested");
        assertEquals(1, entries.size());
        String after = (String) entries.get(0).get("after_text");
        assertTrue(after.contains("PENDING_APPROVAL"), after);
        assertFalse(after.contains("Peter") || after.contains("0772") || after.contains("Calibrated"), after);
        assertEquals(1, fixtures.notificationsFor(admin, "EQUIPMENT_APPROVAL_REQUESTED"));
    }

    @Test
    void anOmittedQuantityMeansOneUnitAndLoanCodesIncrease() throws Exception {
        UUID equipment = fixtures().equipment("TELECOMS", "Multimeter", 2, 2, "AVAILABLE");
        String json = """
                {"equipmentId": "%s", "borrowerName": "Ann", "borrowerContact": "ann@mak.ac.ug",
                 "dateOut": "%s", "dateBack": "%s"}
                """.formatted(equipment, today(), today().plusDays(1));

        String first = mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.outgoingCondition").value(""))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getContentAsString();

        long a = Long.parseLong(((String) JsonPath.read(first, "$.id")).substring(5));
        long b = Long.parseLong(((String) JsonPath.read(second, "$.id")).substring(5));
        assertTrue(b > a);
    }

    @Test
    void everyReportRoleMayRequestButObserversSubmittersAndOtherDepartmentsMayNot() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Soldering station", 20, 20, "AVAILABLE");

        for (String role : EquipmentFixtures.ROLES_THAT_CAN_REPORT) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(request(equipment, 1)))
                    .andExpect(status().isCreated());
        }
        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_REPORT) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(request(equipment, 1)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH).with(fixtures.coordinatorOf("Textile"))
                        .contentType(MediaType.APPLICATION_JSON).content(request(equipment, 1)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH).with(fixtures.coordinatorOf("Telecoms"))
                        .contentType(MediaType.APPLICATION_JSON).content(request(equipment, 1)))
                .andExpect(status().isCreated());
    }

    @Test
    void aRequestWithABadBodyIsAValidationError() throws Exception {
        UUID equipment = fixtures().equipment("TELECOMS", "Function generator", 2, 2, "AVAILABLE");
        String base = "\"equipmentId\": \"%s\", \"borrowerName\": \"Ann\", \"borrowerContact\": \"c\", ".formatted(equipment);

        for (String json : List.of(
                "{" + base + "\"quantity\": 0, \"dateOut\": \"%s\", \"dateBack\": \"%s\"}".formatted(today(), today()),
                "{" + base + "\"dateOut\": \"%s\"}".formatted(today()),
                "{\"equipmentId\": \"%s\", \"borrowerName\": \" \", \"borrowerContact\": \"c\", \"dateOut\": \"%s\", \"dateBack\": \"%s\"}"
                        .formatted(equipment, today(), today()),
                "{" + base + "\"dateOut\": \"%s\", \"dateBack\": \"%s\"}".formatted(today().plusDays(5), today()))) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnprocessableContent());
        }
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + base + "\"dateOut\": \"29 Oct\", \"dateBack\": \"%s\"}".formatted(today())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    void aRequestForUnknownOrForeignEquipmentIsNotFound() throws Exception {
        UUID multimedia = fixtures().equipment("MULTIMEDIA", "Camera", 1, 1, "AVAILABLE");

        for (UUID id : List.of(UUID.randomUUID(), multimedia)) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(request(id, 1)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void aRequestForMoreThanIsAvailableOrForFaultyEquipmentIsAConflict() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID scarce = fixtures.equipment("TELECOMS", "Network analyser", 3, 1, "AVAILABLE");
        UUID faulty = fixtures.equipment("TELECOMS", "Broken probe", 3, 3, "FAULTY");

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(request(scarce, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(request(faulty, 1)))
                .andExpect(status().isConflict());
    }

    // ---- approve ---------------------------------------------------------------------------------------------

    @Test
    void approvingReservesTheUnitsActivatesTheLoanAndRecordsTheManager() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Spectrum analyser", 2, 2, "AVAILABLE");
        String id = requestLoan(equipment, 1);
        String manager = EquipmentFixtures.email("manager");

        mockMvc.perform(post(PATH + "/{id}/approve", id).with(EquipmentFixtures.as(manager, "ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"For the field trial\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.managerApproval.approvedBy").value(manager))
                .andExpect(jsonPath("$.managerApproval.approvedAt").isString())
                .andExpect(jsonPath("$.managerApproval.note").value("For the field trial"));

        entityManager.flush();
        assertEquals(1, fixtures.equipmentRow(equipment).get("available_quantity"));
        assertEquals("AVAILABLE", fixtures.equipmentRow(equipment).get("state"));
        List<Map<String, Object>> entries = fixtures.auditEntries(manager, "equipment_loan.approved");
        assertEquals(1, entries.size());
        String all = entries.get(0).get("before_text") + " " + entries.get(0).get("after_text");
        assertTrue(all.contains("PENDING_APPROVAL") && all.contains("ACTIVE"), all);
        assertFalse(all.contains("field trial") || all.contains("Peter"), all);
    }

    @Test
    void approvingTheLastUnitMarksTheEquipmentLoaned() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Logic analyser", 1, 1, "AVAILABLE");
        String id = requestLoan(equipment, 1);

        mockMvc.perform(post(PATH + "/{id}/approve", id).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isOk());

        entityManager.flush();
        assertEquals(0, fixtures.equipmentRow(equipment).get("available_quantity"));
        assertEquals("LOANED", fixtures.equipmentRow(equipment).get("state"));
    }

    @Test
    void theSecondApprovalForTheLastUnitIsAConflictAndTheCountsStayRight() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Only one", 1, 1, "AVAILABLE");
        String first = requestLoan(equipment, 1);
        String second = requestLoan(equipment, 1);

        mockMvc.perform(post(PATH + "/{id}/approve", first).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isOk());
        mockMvc.perform(post(PATH + "/{id}/approve", second).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Only 0")));

        entityManager.flush();
        assertEquals(0, fixtures.equipmentRow(equipment).get("available_quantity"));
    }

    @Test
    void approvingFaultyEquipmentIsAConflict() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Soon faulty", 2, 2, "AVAILABLE");
        String id = requestLoan(equipment, 1);
        entityManager.flush();
        jdbc.update("UPDATE equipment_items SET state = 'FAULTY' WHERE id = ?", equipment);
        entityManager.clear();

        mockMvc.perform(post(PATH + "/{id}/approve", id).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isConflict());
    }

    @Test
    void aLoanCanOnlyBeDecidedOnce() throws Exception {
        UUID equipment = fixtures().equipment("TELECOMS", "Decided once", 5, 5, "AVAILABLE");
        String id = requestLoan(equipment, 1);
        mockMvc.perform(post(PATH + "/{id}/approve", id).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isOk());

        mockMvc.perform(post(PATH + "/{id}/approve", id).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isConflict());
        mockMvc.perform(post(PATH + "/{id}/decline", id).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isConflict());
    }

    // ---- decline ---------------------------------------------------------------------------------------------

    @Test
    void decliningKeepsTheCountsAndTheReason() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Declined item", 2, 2, "AVAILABLE");
        String id = requestLoan(equipment, 1);
        String manager = EquipmentFixtures.email("manager");

        mockMvc.perform(post(PATH + "/{id}/decline", id).with(EquipmentFixtures.as(manager, "ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Needed for the practical\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.managerApproval.approvedBy").value(manager))
                .andExpect(jsonPath("$.managerApproval.note").value("Needed for the practical"));

        entityManager.flush();
        assertEquals(2, fixtures.equipmentRow(equipment).get("available_quantity"));
        assertEquals(1, fixtures.auditEntries(manager, "equipment_loan.declined").size());
        mockMvc.perform(post(PATH + "/{id}/return", id).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"Fine\"}"))
                .andExpect(status().isConflict());
    }

    // ---- return ----------------------------------------------------------------------------------------------

    @Test
    void returningGivesTheUnitsBackAndKeepsTheCondition() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Returned item", 1, 1, "AVAILABLE");
        String id = requestLoan(equipment, 1);
        mockMvc.perform(post(PATH + "/{id}/approve", id).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isOk());
        String actor = EquipmentFixtures.email("desk");

        mockMvc.perform(post(PATH + "/{id}/return", id).with(EquipmentFixtures.as(actor, "DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"Back in verified working order\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + "/{id}/return", id).with(fixtures.coordinatorOf("Telecoms"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"Back in verified working order\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.incomingCondition").value("Back in verified working order"));

        entityManager.flush();
        assertEquals(1, fixtures.equipmentRow(equipment).get("available_quantity"));
        assertEquals("AVAILABLE", fixtures.equipmentRow(equipment).get("state"));
        mockMvc.perform(post(PATH + "/{id}/return", id).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"Again\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void aLoanThatIsNotOutCannotBeReturnedAndTheConditionIsRequired() throws Exception {
        UUID equipment = fixtures().equipment("TELECOMS", "Pending item", 2, 2, "AVAILABLE");
        String pending = requestLoan(equipment, 1);
        String active = requestLoan(equipment, 1);
        mockMvc.perform(post(PATH + "/{id}/approve", active).with(EquipmentFixtures.as("ADMINISTRATOR")));

        mockMvc.perform(post(PATH + "/{id}/return", pending).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"Fine\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(PATH + "/{id}/return", active).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"  \"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(PATH + "/{id}/return", active).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isBadRequest());
    }

    // ---- overdue and list ------------------------------------------------------------------------------------

    @Test
    void anActiveLoanPastItsReturnDateIsListedAsOverdueAndCanStillBeReturned() throws Exception {
        UUID equipment = fixtures().equipment("TELECOMS", "Late item", 2, 2, "AVAILABLE");
        String id = JsonPath.read(mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"equipmentId": "%s", "quantity": 1, "borrowerName": "Late Borrower", "borrowerContact": "c",
                                 "dateOut": "%s", "dateBack": "%s"}
                                """.formatted(equipment, today().minusDays(10), today().minusDays(1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post(PATH + "/{id}/approve", id).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OVERDUE"));

        mockMvc.perform(get(PATH).with(EquipmentFixtures.as("OBSERVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '%s')].status".formatted(id)).value(org.hamcrest.Matchers.contains("OVERDUE")));
        mockMvc.perform(post(PATH + "/{id}/return", id).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"Returned late\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
    }

    @Test
    void listsNewestFirstOnlyTelecomsLoansForReadRolesInAnItemsWrapper() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Listed item", 5, 5, "AVAILABLE");
        UUID multimedia = fixtures.equipment("MULTIMEDIA", "Other department", 5, 5, "AVAILABLE");
        String older = requestLoan(equipment, 1);
        String newer = requestLoan(equipment, 1);
        jdbc.update("INSERT INTO equipment_loans (id, code, equipment_id, quantity, borrower_name, borrower_type, "
                        + "borrower_contact, date_out, date_back, requested_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, 'B', 'Student', 'c', CURRENT_DATE, CURRENT_DATE, 'a@test.local', now(), now())",
                UUID.randomUUID(), "LOAN-MM-" + UUID.randomUUID().toString().substring(0, 8), multimedia);
        entityManager.flush();
        entityManager.clear();

        String body = mockMvc.perform(get(PATH).with(EquipmentFixtures.as("REVIEWER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$.items[?(@.equipmentName == 'Listed item')].id");
        assertEquals(List.of(newer, older), ids);
        List<Object> foreign = JsonPath.read(body, "$.items[?(@.equipmentName == 'Other department')]");
        assertTrue(foreign.isEmpty());
        mockMvc.perform(get(PATH).with(EquipmentFixtures.as("SUBMITTER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    // ---- roles, departments, unknown loans -------------------------------------------------------------------

    @Test
    void decidingIsForManagersOfTheDepartmentOnly() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID equipment = fixtures.equipment("TELECOMS", "Guarded item", 5, 5, "AVAILABLE");
        String id = requestLoan(equipment, 1);

        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_MANAGE) {
            mockMvc.perform(post(PATH + "/{id}/approve", id).with(EquipmentFixtures.as(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(PATH + "/{id}/decline", id).with(EquipmentFixtures.as(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH + "/{id}/approve", id).with(fixtures.coordinatorOf("Multimedia")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + "/{id}/approve", id).with(fixtures.coordinatorOf("Telecoms")))
                .andExpect(status().isOk());
    }

    @Test
    void anUnknownLoanIsNotFoundForEveryDecision() throws Exception {
        for (String action : List.of("approve", "decline")) {
            mockMvc.perform(post(PATH + "/LOAN-999999/" + action).with(EquipmentFixtures.as("ADMINISTRATOR")))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(post(PATH + "/LOAN-999999/return").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"Fine\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void textThePlatformCannotStoreAndDatesOutOfRangeAreValidationErrors() throws Exception {
        UUID equipment = fixtures().equipment("TELECOMS", "Limit item", 2, 2, "AVAILABLE");

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(equipment, 1).replace("Peter Okello", "Peter\\u0000Okello")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(equipment, 1).replace(today().plusDays(7).toString(), "2999-12-31")
                                .replace("\"dateOut\": \"" + today() + "\"", "\"dateOut\": \"1850-01-01\"")))
                .andExpect(status().isUnprocessableContent());
    }

    private String requestLoan(UUID equipment, int quantity) throws Exception {
        String body = mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(request(equipment, quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String request(UUID equipment, int quantity) {
        return """
                {"equipmentId": "%s", "quantity": %d, "borrowerName": "Peter Okello (S24/1048)",
                 "borrowerType": "Student", "borrowerContact": "0772123456", "dateOut": "%s", "dateBack": "%s",
                 "outgoingCondition": "Calibrated and clean"}
                """.formatted(equipment, quantity, today(), today().plusDays(7));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
