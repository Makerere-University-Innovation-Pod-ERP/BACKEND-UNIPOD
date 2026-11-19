package org.unipod.unipoderpbackend.controller.booking;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.support.AuditTestSupport;
import org.unipod.unipoderpbackend.support.OperationsTestData;
import org.unipod.unipoderpbackend.support.ZodShape;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LabBookingCreateApiIntegrationTest {
    private static final String[] NON_MANAGE_ROLES = {
            "INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER",
            "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER", "SUBMITTER"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID lab;
    private UUID telecoms;

    @BeforeEach
    void seed() {
        telecoms = OperationsTestData.sector(jdbcTemplate, "Telecoms");
        lab = OperationsTestData.exclusiveFacility(jdbcTemplate, "Telecoms", 10);
    }

    @Test
    void booksTheLabAndReturnsTheBookingTheFrontendExpects() throws Exception {
        MockHttpServletRequestBuilder request = book("""
                {"date": "2026-12-08", "start": "09:00", "end": "11:30", "purpose": "Antenna tuning",
                 "groupSize": 4, "customerType": "Student", "bookedBy": "Eng. Kaddu"}""", admin());

        JsonNode created = ZodShape.parse(mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        LabBookingReadApiIntegrationTest.LAB_BOOKING_SHAPE.check(created);
        assertEquals("2026-12-08", created.get("date").asString());
        assertEquals("09:00", created.get("start").asString());
        assertEquals("11:30", created.get("end").asString());
        assertEquals(4, created.get("groupSize").asInt());
        assertEquals("Eng. Kaddu", created.get("bookedBy").asString());
        assertEquals("Student", created.get("customerType").asString());
        assertEquals("CONFIRMED", created.get("status").asString());
        assertEquals("NOT_REQUESTED", created.get("paymentStatus").asString());

        UUID id = UUID.fromString(created.get("id").asString());
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, facility_id, starts_at FROM bookings WHERE id = ?", id);
        assertEquals("APPROVED", row.get("status"));
        assertEquals(lab, row.get("facility_id"));
        assertEquals(Instant.parse("2026-12-08T06:00:00Z"), ((java.sql.Timestamp) row.get("starts_at")).toInstant());
    }

    @Test
    void theNewBookingAppearsOnTheLabCalendar() throws Exception {
        mockMvc.perform(book(body("2026-12-08", "09:00", "10:00"), admin())).andExpect(status().isCreated());

        JsonNode calendar = ZodShape.parse(mockMvc.perform(
                        get("/telecoms/lab-bookings?from=2026-12-07&to=2026-12-13").with(admin()))
                .andReturn().getResponse().getContentAsString());

        assertEquals(1, calendar.get("bookings").size());
    }

    @Test
    void defaultsTheCustomerTypeAndWhoItIsFor() throws Exception {
        String email = "kaddu-" + UUID.randomUUID() + "@test.local";

        mockMvc.perform(book("""
                        {"date": "2026-12-08", "start": "09:00", "end": "10:00", "purpose": "Bench time", "groupSize": 1}""",
                        user(email).roles("ADMINISTRATOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerType").value("Public"))
                .andExpect(jsonPath("$.bookedBy").value(email));
    }

    @Test
    void auditsTheBookingWithoutThePurposeOrTheName() throws Exception {
        JsonNode created = ZodShape.parse(mockMvc.perform(book("""
                {"date": "2026-12-08", "start": "09:00", "end": "11:00", "purpose": "Confidential client prototype",
                 "groupSize": 3, "customerType": "Company", "bookedBy": "Amina Nakato"}""", admin()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asString());

        List<Map<String, Object>> lines = AuditTestSupport.lines(entityManager, jdbcTemplate, id);

        List<Object> actions = lines.stream().map(line -> line.get("action")).toList();
        assertTrue(actions.contains("lab_booking.created"), "actions were " + actions);
        assertTrue(actions.contains("booking.created") && actions.contains("booking.approved"));
        Map<String, Object> ours = lines.stream()
                .filter(line -> "lab_booking.created".equals(line.get("action"))).findFirst().orElseThrow();
        assertEquals(telecoms, ours.get("department_id"));
        assertEquals("ADMINISTRATOR", ours.get("actor_role"));
        for (Map<String, Object> line : lines) {
            AuditTestSupport.assertNoPersonalData(line, "Confidential", "prototype", "Amina", "Nakato");
        }
    }

    @Test
    void aCoordinatorOfTheTelecomsDepartmentMayBookAndAnotherDepartmentsCoordinatorMayNot() throws Exception {
        String ours = "tel-" + UUID.randomUUID() + "@test.local";
        String theirs = "tex-" + UUID.randomUUID() + "@test.local";
        OperationsTestData.member(jdbcTemplate, telecoms, ours);
        OperationsTestData.member(jdbcTemplate, OperationsTestData.sector(jdbcTemplate, "Textile"), theirs);

        mockMvc.perform(book(body("2026-12-08", "09:00", "10:00"), user(ours).roles("DEPARTMENT_COORDINATOR")))
                .andExpect(status().isCreated());
        mockMvc.perform(book(body("2026-12-08", "12:00", "13:00"), user(theirs).roles("DEPARTMENT_COORDINATOR")))
                .andExpect(status().isForbidden());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE facility_id = ?", Integer.class, lab));
    }

    @Test
    void everyOtherRoleIsRefusedAndAnAnonymousCallerIsNotSignedIn() throws Exception {
        for (String role : NON_MANAGE_ROLES) {
            mockMvc.perform(book(body("2026-12-08", "09:00", "10:00"), as(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/telecoms/lab-bookings").contentType(MediaType.APPLICATION_JSON)
                .content(body("2026-12-08", "09:00", "10:00"))).andExpect(status().isUnauthorized());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE facility_id = ?", Integer.class, lab));
    }

    // ----- validation (422 for values, 400 for a body that cannot be read)

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\", \"groupSize\": 1}",
            "{\"date\": \"2026-12-08\", \"end\": \"10:00\", \"purpose\": \"x\", \"groupSize\": 1}",
            "{\"date\": \"2026-12-08\", \"start\": \"09:00\", \"purpose\": \"x\", \"groupSize\": 1}",
            "{\"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"groupSize\": 1}",
            "{\"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"  \", \"groupSize\": 1}",
            "{\"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\"}",
            "{\"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\", \"groupSize\": 0}",
            "{\"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\", \"groupSize\": -2}"
    })
    void refusesAnInvalidFormWithValidationErrors(String json) throws Exception {
        mockMvc.perform(book(json, admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"date\": \"08/12/2026\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\", \"groupSize\": 1}",
            "{\"date\": \"2026-12-08\", \"start\": \"9am\", \"end\": \"10:00\", \"purpose\": \"x\", \"groupSize\": 1}",
            "{\"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\", \"groupSize\": \"four\"}",
            "{\"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\", \"groupSize\": 1, \"colour\": \"red\"}"
    })
    void refusesABodyItCannotReadAsMalformed(String json) throws Exception {
        mockMvc.perform(book(json, admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    void aSessionThatEndsBeforeOrWhenItStartsIsRefused() throws Exception {
        mockMvc.perform(book(body("2026-12-08", "11:00", "10:00"), admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("The booking details are invalid."));
        mockMvc.perform(book(body("2026-12-08", "11:00", "11:00"), admin()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void aGroupLargerThanTheLabIsRefused() throws Exception {
        mockMvc.perform(book("""
                {"date": "2026-12-08", "start": "09:00", "end": "10:00", "purpose": "Big class", "groupSize": 11}""",
                admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("The booking details are invalid."));
    }

    // ----- clashes (409)

    @Test
    void refusesABookingThatOverlapsAnExistingOneAndLeavesNothingBehind() throws Exception {
        mockMvc.perform(book(body("2026-12-08", "09:00", "11:00"), admin())).andExpect(status().isCreated());

        for (String[] clash : new String[][] {
                {"09:00", "11:00"}, {"10:00", "12:00"}, {"08:00", "09:30"}, {"09:30", "10:00"}, {"08:00", "12:00"}}) {
            mockMvc.perform(book(body("2026-12-08", clash[0], clash[1]), admin()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("The facility is already booked for that time."));
        }

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM department_booking_details d JOIN bookings b ON b.id = d.booking_id "
                        + "WHERE b.facility_id = ?", Integer.class, lab));
    }

    @Test
    void backToBackBookingsAreFine() throws Exception {
        mockMvc.perform(book(body("2026-12-08", "09:00", "11:00"), admin())).andExpect(status().isCreated());
        mockMvc.perform(book(body("2026-12-08", "11:00", "12:00"), admin())).andExpect(status().isCreated());
        mockMvc.perform(book(body("2026-12-08", "08:00", "09:00"), admin())).andExpect(status().isCreated());
    }

    @Test
    void aCancelledBookingFreesTheSlot() throws Exception {
        OperationsTestData.booking(jdbcTemplate, lab, Instant.parse("2026-12-08T06:00:00Z"),
                Instant.parse("2026-12-08T08:00:00Z"), "CANCELLED", 2, "Cancelled");

        mockMvc.perform(book(body("2026-12-08", "09:00", "11:00"), admin())).andExpect(status().isCreated());
    }

    @Test
    void aPendingRequestForTheSameHourStillBlocksIt() throws Exception {
        OperationsTestData.booking(jdbcTemplate, lab, Instant.parse("2026-12-08T06:00:00Z"),
                Instant.parse("2026-12-08T08:00:00Z"), "PENDING", 2, "Waiting for approval");

        mockMvc.perform(book(body("2026-12-08", "09:00", "11:00"), admin())).andExpect(status().isConflict());
    }

    @Test
    void refusesAClashWithAClassInTheTimetable() throws Exception {
        OperationsTestData.timetableBlock(jdbcTemplate, lab, "ELE 4201",
                Instant.parse("2026-12-08T06:00:00Z"), Instant.parse("2026-12-08T08:00:00Z"));

        mockMvc.perform(book(body("2026-12-08", "10:00", "12:00"), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("timetable")));
        mockMvc.perform(book(body("2026-12-08", "11:00", "12:00"), admin())).andExpect(status().isCreated());
    }

    @Test
    void refusesABookingWhileTheLabIsUnderMaintenance() throws Exception {
        jdbcTemplate.update("UPDATE facilities SET status = 'MAINTENANCE' WHERE id = ?", lab);

        mockMvc.perform(book(body("2026-12-08", "09:00", "10:00"), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("maintenance")));
    }

    @Test
    void aDepartmentWithNoLabFacilityCannotBeBooked() throws Exception {
        jdbcTemplate.update("UPDATE facilities SET department = 'moved by test' WHERE id = ?", lab);

        mockMvc.perform(book(body("2026-12-08", "09:00", "10:00"), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No facility has been set up for the Telecoms department yet."));
    }

    // ----- helpers

    private static String body(String date, String start, String end) {
        return """
                {"date": "%s", "start": "%s", "end": "%s", "purpose": "Bench session", "groupSize": 2,
                 "customerType": "Student", "bookedBy": "Eng. Kaddu"}""".formatted(date, start, end);
    }

    private static MockHttpServletRequestBuilder book(String json, RequestPostProcessor who) {
        return post("/telecoms/lab-bookings").with(who).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static RequestPostProcessor admin() {
        return as("ADMINISTRATOR");
    }

    private static RequestPostProcessor as(String role) {
        return user("lab-" + UUID.randomUUID() + "@test.local").roles(role);
    }
}
