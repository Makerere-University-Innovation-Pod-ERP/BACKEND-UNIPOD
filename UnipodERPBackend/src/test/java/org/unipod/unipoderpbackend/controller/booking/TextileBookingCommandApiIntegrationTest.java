package org.unipod.unipoderpbackend.controller.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class TextileBookingCommandApiIntegrationTest {
    private static final String[] NON_MANAGE_ROLES = {
            "INNOVATION_COORDINATOR", "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER",
            "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER", "SUBMITTER"
    };
    // Far in the future, so sign-up is open whatever day the tests run.
    private static final Instant STARTS = Instant.parse("2030-03-05T06:00:00Z");

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
        workshop = OperationsTestData.exclusiveFacility(jdbcTemplate, "Textile", 30);
    }

    // ----- cancel

    @Test
    void cancellingFreesTheSlotAndKeepsTheReasonOutOfTheAudit() throws Exception {
        UUID id = booking("APPROVED", "WORKSHOP", null);

        JsonNode cancelled = ZodShape.parse(mockMvc.perform(cancel(id, "Machine is being serviced", admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        TextileBookingApiIntegrationTest.BOOKING_SHAPE.check(cancelled);
        assertEquals("CANCELLED", cancelled.get("status").asString());
        Map<String, Object> detail = jdbcTemplate.queryForMap(
                "SELECT cancel_reason, cancelled_by FROM department_booking_details WHERE booking_id = ?", id);
        assertEquals("Machine is being serviced", detail.get("cancel_reason"));
        List<Map<String, Object>> lines = AuditTestSupport.lines(entityManager, jdbcTemplate, id);
        assertTrue(lines.stream().anyMatch(line -> "textile_booking.cancelled".equals(line.get("action"))));
        for (Map<String, Object> line : lines) {
            AuditTestSupport.assertNoPersonalData(line, "serviced", "Machine");
        }
        // The hour is free again.
        mockMvc.perform(post("/textile/bookings").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"kind": "WORKSHOP", "date": "2030-03-05", "start": "09:00", "end": "10:00", "purpose": "Again"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    void cancellingTwiceOrACompletedBookingIsAConflict() throws Exception {
        UUID id = booking("APPROVED", "WORKSHOP", null);
        mockMvc.perform(cancel(id, "First", admin())).andExpect(status().isOk());

        mockMvc.perform(cancel(id, "Second", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This booking is already cancelled."));

        UUID done = booking("COMPLETED", "WORKSHOP", null);
        mockMvc.perform(cancel(done, "Too late", admin())).andExpect(status().isConflict());
    }

    @Test
    void aCancelNeedsAReason() throws Exception {
        UUID id = booking("APPROVED", "WORKSHOP", null);

        mockMvc.perform(cancelBody(id, "{\"reason\": \"  \"}", admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        mockMvc.perform(cancelBody(id, "{}", admin())).andExpect(status().isUnprocessableContent());
        mockMvc.perform(cancelBody(id, "nope", admin())).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownBookingOrAnotherDepartmentsBookingIsNotFound() throws Exception {
        mockMvc.perform(cancel(UUID.randomUUID(), "x", admin())).andExpect(status().isNotFound());

        UUID lab = OperationsTestData.exclusiveFacility(jdbcTemplate, "Telecoms", 10);
        UUID labBooking = OperationsTestData.booking(jdbcTemplate, lab, STARTS, STARTS.plusSeconds(3600), "APPROVED", 2, "Lab");
        OperationsTestData.bookingDetail(jdbcTemplate, labBooking, "TELECOMS", "LAB", "a@test.local", null, null, null);
        mockMvc.perform(cancel(labBooking, "x", admin())).andExpect(status().isNotFound());
        mockMvc.perform(addAttendee(labBooking, "Aisha", admin())).andExpect(status().isNotFound());
    }

    // ----- sign-ups

    @Test
    void signsSomeoneUpAndReturnsTheWholeBooking() throws Exception {
        UUID id = booking("APPROVED", "TRAINING", 3);

        JsonNode booking = ZodShape.parse(mockMvc.perform(addAttendee(id, "  Brian Kato ", admin()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        TextileBookingApiIntegrationTest.BOOKING_SHAPE.check(booking);
        assertEquals(1, booking.get("attendees").size());
        assertEquals("Brian Kato", booking.get("attendees").get(0).get("name").asString());
        assertEquals(false, booking.get("attendees").get(0).get("attended").asBoolean());
        List<Map<String, Object>> lines = AuditTestSupport.lines(entityManager, jdbcTemplate,
                UUID.fromString(booking.get("attendees").get(0).get("id").asString()));
        assertEquals("textile_booking.attendee_added", lines.get(0).get("action"));
        AuditTestSupport.assertNoPersonalData(lines.get(0), "Brian", "Kato");
    }

    @Test
    void theLastPlaceIsTakenAndTheNextPersonIsTurnedAway() throws Exception {
        UUID id = booking("APPROVED", "TRAINING", 2);
        mockMvc.perform(addAttendee(id, "One", admin())).andExpect(status().isCreated());
        mockMvc.perform(addAttendee(id, "Two", admin())).andExpect(status().isCreated());

        mockMvc.perform(addAttendee(id, "Three", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This session is full."));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM booking_attendees WHERE booking_id = ?", Integer.class, id));
    }

    @Test
    void aTrainingSessionWithoutALimitTakesAnyoneAndAWorkshopTakesNoSignUps() throws Exception {
        UUID open = booking("APPROVED", "TRAINING", null);
        mockMvc.perform(addAttendee(open, "One", admin())).andExpect(status().isCreated());
        mockMvc.perform(addAttendee(open, "Two", admin())).andExpect(status().isCreated());

        UUID workshopBooking = booking("APPROVED", "WORKSHOP", null);
        mockMvc.perform(addAttendee(workshopBooking, "One", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only training sessions take sign-ups."));
    }

    @Test
    void aCancelledOrFinishedSessionTakesNoSignUps() throws Exception {
        UUID cancelled = booking("CANCELLED", "TRAINING", 5);
        mockMvc.perform(addAttendee(cancelled, "One", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This booking is cancelled."));

        UUID past = OperationsTestData.booking(jdbcTemplate, workshop, Instant.parse("2020-03-05T06:00:00Z"),
                Instant.parse("2020-03-05T08:00:00Z"), "APPROVED", 5, "Past class");
        OperationsTestData.bookingDetail(jdbcTemplate, past, "TEXTILE", "TRAINING", "a@test.local", null, null, 5);
        mockMvc.perform(addAttendee(past, "One", admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Sign-up is closed for this session."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\": \"\"}", "{\"name\": \"   \"}"})
    void aSignUpNeedsAName(String json) throws Exception {
        UUID id = booking("APPROVED", "TRAINING", 5);

        mockMvc.perform(post("/textile/bookings/{id}/attendees", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ----- attendance

    @Test
    void marksSomeoneAsAttendedAndBack() throws Exception {
        UUID id = booking("APPROVED", "TRAINING", 5);
        UUID attendee = signUp(id, "Aisha Nalwoga");

        JsonNode marked = ZodShape.parse(mockMvc.perform(attendance(id, attendee, "true", admin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        TextileBookingApiIntegrationTest.BOOKING_SHAPE.check(marked);
        assertEquals(true, marked.get("attendees").get(0).get("attended").asBoolean());

        mockMvc.perform(attendance(id, attendee, "false", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendees[0].attended").value(false));
        List<Map<String, Object>> lines = AuditTestSupport.lines(entityManager, jdbcTemplate, attendee);
        assertEquals(2, lines.size());
        AuditTestSupport.assertNoPersonalData(lines.get(0), "Aisha", "Nalwoga");
    }

    @Test
    void repeatingTheSameAttendanceWritesNoAuditLine() throws Exception {
        UUID id = booking("APPROVED", "TRAINING", 5);
        UUID attendee = signUp(id, "Aisha");

        mockMvc.perform(attendance(id, attendee, "false", admin())).andExpect(status().isOk());

        assertEquals(0, AuditTestSupport.lines(entityManager, jdbcTemplate, attendee).size());
    }

    @Test
    void attendanceForSomeoneNotOnThisBookingOrOnACancelledOneIsRefused() throws Exception {
        UUID id = booking("APPROVED", "TRAINING", 5);
        UUID other = booking("APPROVED", "TRAINING", 5);
        UUID stranger = signUp(other, "Moses");
        mockMvc.perform(attendance(id, stranger, "true", admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Participant not found."));
        mockMvc.perform(attendance(id, UUID.randomUUID(), "true", admin())).andExpect(status().isNotFound());

        UUID cancelled = booking("CANCELLED", "TRAINING", 5);
        UUID attendee = signUp(cancelled, "Moses");
        mockMvc.perform(attendance(cancelled, attendee, "true", admin())).andExpect(status().isConflict());
    }

    @Test
    void attendedMustBeABoolean() throws Exception {
        UUID id = booking("APPROVED", "TRAINING", 5);
        UUID attendee = signUp(id, "Aisha");

        mockMvc.perform(put("/textile/bookings/{id}/attendees/{a}", id, attendee).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(put("/textile/bookings/{id}/attendees/{a}", id, attendee).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attended\": \"yes\"}"))
                .andExpect(status().isBadRequest());
    }

    // ----- who may

    @Test
    void onlyTheTextileDepartmentsManagersMayChangeBookings() throws Exception {
        UUID id = booking("APPROVED", "TRAINING", 5);
        UUID attendee = signUp(id, "Aisha");
        String ours = "tex-" + UUID.randomUUID() + "@test.local";
        String theirs = "tel-" + UUID.randomUUID() + "@test.local";
        OperationsTestData.member(jdbcTemplate, textile, ours);
        OperationsTestData.member(jdbcTemplate, OperationsTestData.sector(jdbcTemplate, "Telecoms"), theirs);

        mockMvc.perform(addAttendee(id, "Brian", user(ours).roles("DEPARTMENT_COORDINATOR")))
                .andExpect(status().isCreated());
        RequestPostProcessor outsider = user(theirs).roles("DEPARTMENT_COORDINATOR");
        mockMvc.perform(addAttendee(id, "Brian", outsider)).andExpect(status().isForbidden());
        mockMvc.perform(attendance(id, attendee, "true", outsider)).andExpect(status().isForbidden());
        mockMvc.perform(cancel(id, "x", outsider)).andExpect(status().isForbidden());

        for (String role : NON_MANAGE_ROLES) {
            RequestPostProcessor who = user("x-" + UUID.randomUUID() + "@test.local").roles(role);
            mockMvc.perform(addAttendee(id, "Brian", who)).andExpect(status().isForbidden());
            mockMvc.perform(attendance(id, attendee, "true", who)).andExpect(status().isForbidden());
            mockMvc.perform(cancel(id, "x", who)).andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/textile/bookings/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"x\"}")).andExpect(status().isUnauthorized());
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT status FROM bookings WHERE id = ?", String.class, id));
    }

    // ----- helpers

    private UUID booking(String stored, String kind, Integer capacity) {
        UUID id = OperationsTestData.booking(jdbcTemplate, workshop, STARTS, STARTS.plusSeconds(10_800), stored,
                capacity == null ? 1 : capacity, "Class");
        OperationsTestData.bookingDetail(jdbcTemplate, id, "TEXTILE", kind, "faith@test.local", null, null, capacity);
        return id;
    }

    private UUID signUp(UUID booking, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO booking_attendees (id, booking_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, booking, name, Timestamp.from(STARTS), Timestamp.from(STARTS));
        return id;
    }

    private static MockHttpServletRequestBuilder cancel(UUID id, String reason, RequestPostProcessor who) {
        return cancelBody(id, "{\"reason\": \"" + reason + "\"}", who);
    }

    private static MockHttpServletRequestBuilder cancelBody(UUID id, String json, RequestPostProcessor who) {
        return post("/textile/bookings/{id}/cancel", id).with(who).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static MockHttpServletRequestBuilder addAttendee(UUID id, String name, RequestPostProcessor who) {
        return post("/textile/bookings/{id}/attendees", id).with(who).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\"}");
    }

    private static MockHttpServletRequestBuilder attendance(UUID id, UUID attendee, String attended,
                                                            RequestPostProcessor who) {
        return put("/textile/bookings/{id}/attendees/{a}", id, attendee).with(who)
                .contentType(MediaType.APPLICATION_JSON).content("{\"attended\": " + attended + "}");
    }

    private static RequestPostProcessor admin() {
        return user("tex-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR");
    }
}
