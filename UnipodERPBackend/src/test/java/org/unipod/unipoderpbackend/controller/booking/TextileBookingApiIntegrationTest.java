package org.unipod.unipoderpbackend.controller.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
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
class TextileBookingApiIntegrationTest {
    // The frontend's textile bookingSchema, attendeeSchema and timetableEntrySchema (services/textile.js).
    static final ZodShape ATTENDEE_SHAPE = ZodShape.object().string("id").string("name").bool("attended");
    static final ZodShape BOOKING_SHAPE = ZodShape.object()
            .string("id").enumOf("kind", "WORKSHOP", "TRAINING").string("date").string("start").string("end")
            .string("purpose").nullableString("machineId").nullableNumber("capacity").string("bookedBy")
            .enumOf("status", "APPROVED", "PENDING", "CANCELLED").arrayOf("attendees", ATTENDEE_SHAPE);
    static final ZodShape CALENDAR_SHAPE = ZodShape.object()
            .arrayOf("bookings", BOOKING_SHAPE).arrayOf("timetable", LabBookingReadApiIntegrationTest.TIMETABLE_SHAPE);

    private static final String WEEK = "?from=2026-12-07&to=2026-12-13";
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

    private UUID workshop;
    private UUID textile;

    @BeforeEach
    void seed() {
        textile = OperationsTestData.sector(jdbcTemplate, "Textile");
        workshop = OperationsTestData.exclusiveFacility(jdbcTemplate, "Textile", 20);
    }

    // ----- reading the calendar

    @Test
    void listsTheWeeksBookingsWithKampalaTimesAndTheirSignUps() throws Exception {
        UUID training = booking("2026-12-11T06:00:00Z", "2026-12-11T09:00:00Z", "APPROVED", "TRAINING", 16,
                "Pattern-cutting class", "m-cutting");
        signUp(training, "Brian Kato", "2026-12-01T08:00:00Z", false);
        signUp(training, "Aisha Nalwoga", "2026-12-01T09:00:00Z", true);
        booking("2026-12-08T10:00:00Z", "2026-12-08T13:00:00Z", "APPROVED", "WORKSHOP", null, "Tote bags", null);

        JsonNode calendar = calendar(WEEK);

        CALENDAR_SHAPE.check(calendar);
        JsonNode bookings = calendar.get("bookings");
        assertEquals(2, bookings.size());
        assertEquals("Tote bags", bookings.get(0).get("purpose").asString());
        assertEquals("WORKSHOP", bookings.get(0).get("kind").asString());
        assertEquals("13:00", bookings.get(0).get("start").asString());
        assertEquals("16:00", bookings.get(0).get("end").asString());
        assertTrue(bookings.get(0).get("capacity").isNull());
        assertTrue(bookings.get(0).get("machineId").isNull());
        JsonNode first = bookings.get(1);
        assertEquals(training.toString(), first.get("id").asString());
        assertEquals("TRAINING", first.get("kind").asString());
        assertEquals("2026-12-11", first.get("date").asString());
        assertEquals("m-cutting", first.get("machineId").asString());
        assertEquals(16, first.get("capacity").asInt());
        assertEquals("APPROVED", first.get("status").asString());
        assertEquals(2, first.get("attendees").size());
        assertEquals("Brian Kato", first.get("attendees").get(0).get("name").asString());
        assertEquals(false, first.get("attendees").get(0).get("attended").asBoolean());
        assertEquals(true, first.get("attendees").get(1).get("attended").asBoolean());
    }

    @Test
    void showsStoredStatusesTheWayTheWorkshopScreenKnowsThem() throws Exception {
        booking("2026-12-08T06:00:00Z", "2026-12-08T07:00:00Z", "CHECKED_IN", "WORKSHOP", null, "In progress", null);
        booking("2026-12-09T06:00:00Z", "2026-12-09T07:00:00Z", "COMPLETED", "WORKSHOP", null, "Finished", null);
        booking("2026-12-10T06:00:00Z", "2026-12-10T07:00:00Z", "PENDING", "WORKSHOP", null, "Waiting", null);
        booking("2026-12-11T06:00:00Z", "2026-12-11T07:00:00Z", "CANCELLED", "WORKSHOP", null, "Cancelled", null);
        booking("2026-12-12T06:00:00Z", "2026-12-12T07:00:00Z", "REJECTED", "WORKSHOP", null, "Rejected", null);

        JsonNode bookings = calendar(WEEK).get("bookings");

        java.util.List<String> shown = new java.util.ArrayList<>();
        bookings.forEach(booking -> shown.add(booking.get("status").asString()));
        assertEquals(List.of("APPROVED", "APPROVED", "PENDING", "CANCELLED"), shown);
    }

    @Test
    void leavesOutOtherDepartmentsAndBookingsWithoutADetailRow() throws Exception {
        UUID lab = OperationsTestData.exclusiveFacility(jdbcTemplate, "Telecoms", 20);
        UUID labBooking = OperationsTestData.booking(jdbcTemplate, lab, Instant.parse("2026-12-08T06:00:00Z"),
                Instant.parse("2026-12-08T07:00:00Z"), "APPROVED", 2, "Lab session");
        OperationsTestData.bookingDetail(jdbcTemplate, labBooking, "TELECOMS", "LAB", "a@test.local", null, null, null);
        OperationsTestData.booking(jdbcTemplate, workshop, Instant.parse("2026-12-09T06:00:00Z"),
                Instant.parse("2026-12-09T07:00:00Z"), "APPROVED", 2, "General booking");

        assertEquals(0, calendar(WEEK).get("bookings").size());
    }

    @Test
    void derivesTheClassTimetableFromTheWorkshopsBlocks() throws Exception {
        OperationsTestData.timetableBlock(jdbcTemplate, workshop, "BSc Fashion Design - Pattern Cutting I",
                Instant.parse("2026-12-07T06:00:00Z"), Instant.parse("2026-12-07T09:00:00Z"));
        OperationsTestData.timetableBlock(jdbcTemplate, workshop, "Diploma Textiles - Garment Construction",
                Instant.parse("2026-12-09T11:00:00Z"), Instant.parse("2026-12-09T14:00:00Z"));

        JsonNode timetable = calendar(WEEK).get("timetable");

        assertEquals(2, timetable.size());
        assertEquals(1, timetable.get(0).get("weekday").asInt());
        assertEquals("09:00", timetable.get(0).get("start").asString());
        assertEquals("12:00", timetable.get(0).get("end").asString());
        assertEquals(3, timetable.get(1).get("weekday").asInt());
        assertEquals("14:00", timetable.get(1).get("start").asString());
    }

    @Test
    void aDepartmentWithNoFacilityHasAnEmptyCalendar() throws Exception {
        jdbcTemplate.update("UPDATE facilities SET department = 'moved by test' WHERE id = ?", workshop);

        JsonNode calendar = calendar(WEEK);

        CALENDAR_SHAPE.check(calendar);
        assertEquals(0, calendar.get("bookings").size());
        assertEquals(0, calendar.get("timetable").size());
    }

    @Test
    void aBadRangeIsRefused() throws Exception {
        mockMvc.perform(get("/textile/bookings?from=2026-12-13&to=2026-12-07").with(as("OBSERVER")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get("/textile/bookings?from=tomorrow").with(as("OBSERVER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void everyRoleExceptSubmitterMayReadTheCalendar() throws Exception {
        for (String role : new String[] {"ADMINISTRATOR", "DEPARTMENT_COORDINATOR", "INNOVATION_COORDINATOR",
                "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON",
                "FINANCE_OFFICER", "OBSERVER"}) {
            mockMvc.perform(get("/textile/bookings" + WEEK).with(as(role))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/textile/bookings" + WEEK).with(as("SUBMITTER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/textile/bookings" + WEEK)).andExpect(status().isUnauthorized());
    }

    // ----- creating a booking

    @Test
    void bookingAWorkshopReturnsTheBookingTheFrontendExpects() throws Exception {
        String email = "faith-" + UUID.randomUUID() + "@test.local";

        JsonNode created = ZodShape.parse(mockMvc.perform(create("""
                {"kind": "WORKSHOP", "date": "2026-12-08", "start": "14:00", "end": "17:00",
                 "purpose": "Student pattern-cutting practice", "machineId": "m-sew-1", "capacity": null}""",
                user(email).roles("ADMINISTRATOR")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        BOOKING_SHAPE.check(created);
        assertEquals("WORKSHOP", created.get("kind").asString());
        assertEquals("2026-12-08", created.get("date").asString());
        assertEquals("14:00", created.get("start").asString());
        assertEquals("17:00", created.get("end").asString());
        assertEquals("m-sew-1", created.get("machineId").asString());
        assertTrue(created.get("capacity").isNull());
        assertEquals(email, created.get("bookedBy").asString());
        assertEquals("APPROVED", created.get("status").asString());
        assertEquals(0, created.get("attendees").size());
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT status FROM bookings WHERE id = ?", String.class, UUID.fromString(created.get("id").asString())));
    }

    @Test
    void aTrainingSessionKeepsItsNumberOfPlaces() throws Exception {
        mockMvc.perform(create("""
                {"kind": "TRAINING", "date": "2026-12-08", "start": "09:00", "end": "12:00",
                 "purpose": "Pattern-cutting class", "machineId": null, "capacity": 12}""", admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("TRAINING"))
                .andExpect(jsonPath("$.capacity").value(12))
                .andExpect(jsonPath("$.machineId").doesNotExist());
    }

    @Test
    void theCreatedBookingAppearsOnTheCalendar() throws Exception {
        mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "09:00", "10:00"), admin())).andExpect(status().isCreated());

        assertEquals(1, calendar(WEEK).get("bookings").size());
    }

    @Test
    void auditsTheBookingWithoutThePurpose() throws Exception {
        JsonNode created = ZodShape.parse(mockMvc.perform(create("""
                {"kind": "TRAINING", "date": "2026-12-08", "start": "09:00", "end": "12:00",
                 "purpose": "Private tailoring masterclass", "capacity": 8}""", admin()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID id = UUID.fromString(created.get("id").asString());

        List<Map<String, Object>> lines = AuditTestSupport.lines(entityManager, jdbcTemplate, id);

        Map<String, Object> ours = lines.stream()
                .filter(line -> "textile_booking.created".equals(line.get("action"))).findFirst().orElseThrow();
        assertEquals(textile, ours.get("department_id"));
        for (Map<String, Object> line : lines) {
            AuditTestSupport.assertNoPersonalData(line, "masterclass", "tailoring", "Private");
        }
    }

    @Test
    void aCoordinatorOfTheTextileDepartmentMayBookAndAnotherDepartmentsCoordinatorMayNot() throws Exception {
        String ours = "tex-" + UUID.randomUUID() + "@test.local";
        String theirs = "tel-" + UUID.randomUUID() + "@test.local";
        OperationsTestData.member(jdbcTemplate, textile, ours);
        OperationsTestData.member(jdbcTemplate, OperationsTestData.sector(jdbcTemplate, "Telecoms"), theirs);

        mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "09:00", "10:00"),
                user(ours).roles("DEPARTMENT_COORDINATOR"))).andExpect(status().isCreated());
        mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "12:00", "13:00"),
                user(theirs).roles("DEPARTMENT_COORDINATOR"))).andExpect(status().isForbidden());
    }

    @Test
    void everyOtherRoleIsRefusedAndAnAnonymousCallerIsNotSignedIn() throws Exception {
        for (String role : NON_MANAGE_ROLES) {
            mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "09:00", "10:00"), as(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/textile/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(body("WORKSHOP", "2026-12-08", "09:00", "10:00"))).andExpect(status().isUnauthorized());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bookings WHERE facility_id = ?", Integer.class, workshop));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\"}",
            "{\"kind\": \"WORKSHOP\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\"}",
            "{\"kind\": \"WORKSHOP\", \"date\": \"2026-12-08\", \"end\": \"10:00\", \"purpose\": \"x\"}",
            "{\"kind\": \"WORKSHOP\", \"date\": \"2026-12-08\", \"start\": \"09:00\", \"purpose\": \"x\"}",
            "{\"kind\": \"WORKSHOP\", \"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\"}",
            "{\"kind\": \"WORKSHOP\", \"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \" \"}",
            "{\"kind\": \"TRAINING\", \"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\", \"capacity\": 0}"
    })
    void refusesAnInvalidFormWithValidationErrors(String json) throws Exception {
        mockMvc.perform(create(json, admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "{\"kind\": \"SEMINAR\", \"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\"}",
            "{\"kind\": \"LAB\", \"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\"}",
            "{\"kind\": \"WORKSHOP\", \"date\": \"8 Dec\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\"}",
            "{\"kind\": \"WORKSHOP\", \"date\": \"2026-12-08\", \"start\": \"09:00\", \"end\": \"10:00\", \"purpose\": \"x\", \"photo\": \"a.png\"}"
    })
    void refusesABodyItCannotReadAsMalformed(String json) throws Exception {
        mockMvc.perform(create(json, admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    void aWorkshopCannotHaveANumberOfPlaces() throws Exception {
        mockMvc.perform(create("""
                {"kind": "WORKSHOP", "date": "2026-12-08", "start": "09:00", "end": "10:00",
                 "purpose": "Practice", "capacity": 5}""", admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("The booking details are invalid."));
    }

    @Test
    void aTrainingSessionLargerThanTheWorkshopIsRefused() throws Exception {
        mockMvc.perform(create("""
                {"kind": "TRAINING", "date": "2026-12-08", "start": "09:00", "end": "10:00",
                 "purpose": "Big class", "capacity": 21}""", admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("The booking details are invalid."));
    }

    @Test
    void aSessionThatEndsBeforeItStartsIsRefused() throws Exception {
        mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "12:00", "11:00"), admin()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void refusesOverlapsWithBookingsAndClassesButNotBackToBackBookings() throws Exception {
        OperationsTestData.timetableBlock(jdbcTemplate, workshop, "Pattern Cutting I",
                Instant.parse("2026-12-08T06:00:00Z"), Instant.parse("2026-12-08T09:00:00Z"));
        mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "13:00", "15:00"), admin())).andExpect(status().isCreated());

        mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "14:00", "16:00"), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The facility is already booked for that time."));
        mockMvc.perform(create(body("TRAINING", "2026-12-08", "10:00", "11:00"), admin()))
                .andExpect(status().isConflict());
        mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "15:00", "16:00"), admin())).andExpect(status().isCreated());
        mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "12:00", "13:00"), admin())).andExpect(status().isCreated());
    }

    @Test
    void aDepartmentWithNoWorkshopFacilityCannotBeBooked() throws Exception {
        jdbcTemplate.update("UPDATE facilities SET department = 'moved by test' WHERE id = ?", workshop);

        mockMvc.perform(create(body("WORKSHOP", "2026-12-08", "09:00", "10:00"), admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No facility has been set up for the Textile department yet."));
    }

    // ----- helpers

    private UUID booking(String starts, String ends, String stored, String kind, Integer capacity, String purpose,
                         String machineId) {
        UUID id = OperationsTestData.booking(jdbcTemplate, workshop, Instant.parse(starts), Instant.parse(ends),
                stored, capacity == null ? 1 : capacity, purpose);
        OperationsTestData.bookingDetail(jdbcTemplate, id, "TEXTILE", kind, "faith@test.local", null, machineId, capacity);
        return id;
    }

    private void signUp(UUID booking, String name, String at, boolean attended) {
        jdbcTemplate.update(
                "INSERT INTO booking_attendees (id, booking_id, name, attended, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), booking, name, attended, Timestamp.from(Instant.parse(at)),
                Timestamp.from(Instant.parse(at)));
    }

    private JsonNode calendar(String query) throws Exception {
        return ZodShape.parse(mockMvc.perform(get("/textile/bookings" + query).with(as("OBSERVER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String body(String kind, String date, String start, String end) {
        return """
                {"kind": "%s", "date": "%s", "start": "%s", "end": "%s", "purpose": "Practice session"%s}"""
                .formatted(kind, date, start, end, "TRAINING".equals(kind) ? ", \"capacity\": 10" : "");
    }

    private static MockHttpServletRequestBuilder create(String json, RequestPostProcessor who) {
        return post("/textile/bookings").with(who).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static RequestPostProcessor admin() {
        return as("ADMINISTRATOR");
    }

    private static RequestPostProcessor as(String role) {
        return user("tex-" + UUID.randomUUID() + "@test.local").roles(role);
    }
}
