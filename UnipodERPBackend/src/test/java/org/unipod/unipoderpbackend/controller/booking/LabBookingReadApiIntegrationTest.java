package org.unipod.unipoderpbackend.controller.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.support.OperationsTestData;
import org.unipod.unipoderpbackend.support.ZodShape;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LabBookingReadApiIntegrationTest {
    // The frontend's labBookingSchema and timetableEntrySchema (services/telecoms.js).
    static final ZodShape LAB_BOOKING_SHAPE = ZodShape.object()
            .string("id").string("purpose").string("date").string("start").string("end").number("groupSize")
            .string("bookedBy").string("customerType").enumOf("status", "CONFIRMED", "CANCELLED")
            .enumOf("paymentStatus", "NOT_REQUESTED", "AWAITING_PROOF", "PROOF_SUBMITTED", "CONFIRMED", "REJECTED")
            .string("createdAt");
    static final ZodShape TIMETABLE_SHAPE = ZodShape.object()
            .string("id").number("weekday").string("start").string("end").string("label");
    static final ZodShape CALENDAR_SHAPE = ZodShape.object()
            .arrayOf("bookings", LAB_BOOKING_SHAPE).arrayOf("timetable", TIMETABLE_SHAPE);

    // Monday 7 December 2026; Kampala is UTC+3 all year.
    private static final String WEEK = "?from=2026-12-07&to=2026-12-13";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private UUID lab;

    @BeforeEach
    void seedLab() {
        lab = OperationsTestData.exclusiveFacility(jdbcTemplate, "Telecoms", 20);
    }

    @Test
    void listsTheWeeksLabBookingsWithKampalaDatesAndTimes() throws Exception {
        UUID id = labBooking("2026-12-08T06:00:00Z", "2026-12-08T08:30:00Z", "APPROVED", 4, "Antenna tuning",
                "Eng. Kaddu", "Student");

        JsonNode calendar = calendar(WEEK);

        CALENDAR_SHAPE.check(calendar);
        assertEquals(1, calendar.get("bookings").size());
        JsonNode booking = calendar.get("bookings").get(0);
        assertEquals(id.toString(), booking.get("id").asString());
        assertEquals("Antenna tuning", booking.get("purpose").asString());
        assertEquals("2026-12-08", booking.get("date").asString());
        assertEquals("09:00", booking.get("start").asString());
        assertEquals("11:30", booking.get("end").asString());
        assertEquals(4, booking.get("groupSize").asInt());
        assertEquals("Eng. Kaddu", booking.get("bookedBy").asString());
        assertEquals("Student", booking.get("customerType").asString());
        assertEquals("CONFIRMED", booking.get("status").asString());
        assertEquals("NOT_REQUESTED", booking.get("paymentStatus").asString());
    }

    @Test
    void ordersBookingsByStartAndKeepsOnlyTheRequestedDates() throws Exception {
        UUID later = labBooking("2026-12-10T06:00:00Z", "2026-12-10T07:00:00Z", "APPROVED", 2, "Later", "A", "Student");
        UUID sooner = labBooking("2026-12-08T06:00:00Z", "2026-12-08T07:00:00Z", "APPROVED", 2, "Sooner", "A", "Student");
        labBooking("2026-12-14T06:00:00Z", "2026-12-14T07:00:00Z", "APPROVED", 2, "Next week", "A", "Student");
        // Ends just before the week begins in Kampala (Sunday 23:30 local), so it belongs to the week before.
        labBooking("2026-12-06T20:00:00Z", "2026-12-06T20:30:00Z", "APPROVED", 2, "Week before", "A", "Student");

        JsonNode bookings = calendar(WEEK).get("bookings");

        assertEquals(2, bookings.size());
        assertEquals(sooner.toString(), bookings.get(0).get("id").asString());
        assertEquals(later.toString(), bookings.get(1).get("id").asString());
    }

    @Test
    void showsCheckedInAndCompletedBookingsAsConfirmed() throws Exception {
        labBooking("2026-12-08T06:00:00Z", "2026-12-08T07:00:00Z", "CHECKED_IN", 2, "Running", "A", "Student");
        labBooking("2026-12-09T06:00:00Z", "2026-12-09T07:00:00Z", "COMPLETED", 2, "Done", "A", "Student");

        JsonNode bookings = calendar(WEEK).get("bookings");

        assertEquals(2, bookings.size());
        bookings.forEach(booking -> assertEquals("CONFIRMED", booking.get("status").asString()));
    }

    @Test
    void leavesOutCancelledPendingAndRejectedBookings() throws Exception {
        labBooking("2026-12-08T06:00:00Z", "2026-12-08T07:00:00Z", "CANCELLED", 2, "Cancelled", "A", "Student");
        labBooking("2026-12-09T06:00:00Z", "2026-12-09T07:00:00Z", "PENDING", 2, "Pending", "A", "Student");
        labBooking("2026-12-10T06:00:00Z", "2026-12-10T07:00:00Z", "REJECTED", 2, "Rejected", "A", "Student");

        assertEquals(0, calendar(WEEK).get("bookings").size());
    }

    @Test
    void leavesOutOtherDepartmentsAndBookingsWithoutADetailRow() throws Exception {
        UUID workshop = OperationsTestData.exclusiveFacility(jdbcTemplate, "Textile", 20);
        UUID textile = OperationsTestData.booking(jdbcTemplate, workshop, Instant.parse("2026-12-08T06:00:00Z"),
                Instant.parse("2026-12-08T07:00:00Z"), "APPROVED", 2, "Workshop");
        OperationsTestData.bookingDetail(jdbcTemplate, textile, "TEXTILE", "WORKSHOP", "a@test.local", null, null, null);
        // A booking made through the general /bookings endpoint has no department detail.
        OperationsTestData.booking(jdbcTemplate, lab, Instant.parse("2026-12-09T06:00:00Z"),
                Instant.parse("2026-12-09T07:00:00Z"), "APPROVED", 2, "General booking");

        assertEquals(0, calendar(WEEK).get("bookings").size());
    }

    @Test
    void aMissingCustomerTypeReadsAsPublic() throws Exception {
        labBooking("2026-12-08T06:00:00Z", "2026-12-08T07:00:00Z", "APPROVED", 2, "Walk-in", "Visitor", null);

        assertEquals("Public", calendar(WEEK).get("bookings").get(0).get("customerType").asString());
    }

    @Test
    void takesThePaymentStatusFromTheLinkedInvoice() throws Exception {
        UUID paid = labBooking("2026-12-08T06:00:00Z", "2026-12-08T07:00:00Z", "APPROVED", 2, "Paid", "A", "Company");
        UUID waiting = labBooking("2026-12-09T06:00:00Z", "2026-12-09T07:00:00Z", "APPROVED", 2, "Waiting", "A", "Company");
        UUID rejected = labBooking("2026-12-10T06:00:00Z", "2026-12-10T07:00:00Z", "APPROVED", 2, "Rejected", "A", "Company");
        UUID none = labBooking("2026-12-11T06:00:00Z", "2026-12-11T07:00:00Z", "APPROVED", 2, "Free", "A", "Internal");
        Instant at = Instant.parse("2026-12-01T08:00:00Z");
        OperationsTestData.invoice(jdbcTemplate, "BOOKING", paid.toString(), "a@test.local", "PAID", at);
        OperationsTestData.invoice(jdbcTemplate, "BOOKING", waiting.toString(), "a@test.local", "WAITING_FOR_CHECK", at);
        UUID invoice = OperationsTestData.invoice(jdbcTemplate, "BOOKING", rejected.toString(), "a@test.local",
                "WAITING_FOR_PROOF", at);
        OperationsTestData.proof(jdbcTemplate, invoice, "REJECTED", at.plusSeconds(60));

        JsonNode bookings = calendar(WEEK).get("bookings");

        assertEquals("CONFIRMED", paymentOf(bookings, paid));
        assertEquals("PROOF_SUBMITTED", paymentOf(bookings, waiting));
        assertEquals("REJECTED", paymentOf(bookings, rejected));
        assertEquals("NOT_REQUESTED", paymentOf(bookings, none));
    }

    @Test
    void derivesTheWeeklyTimetableFromTheLabsTimetableBlocks() throws Exception {
        // Tuesday 09:00-11:00 local, and the same class again the following Tuesday.
        OperationsTestData.timetableBlock(jdbcTemplate, lab, "ELE 4201 Transmission Lab",
                Instant.parse("2026-12-08T06:00:00Z"), Instant.parse("2026-12-08T08:00:00Z"));
        OperationsTestData.timetableBlock(jdbcTemplate, lab, "ELE 3205 RF Practical",
                Instant.parse("2026-12-10T11:00:00Z"), Instant.parse("2026-12-10T13:00:00Z"));
        // Another facility's class and a class outside the window must not appear.
        UUID other = OperationsTestData.facility(jdbcTemplate, "Textile", 10);
        OperationsTestData.timetableBlock(jdbcTemplate, other, "Not ours",
                Instant.parse("2026-12-09T06:00:00Z"), Instant.parse("2026-12-09T08:00:00Z"));
        OperationsTestData.timetableBlock(jdbcTemplate, lab, "Outside",
                Instant.parse("2026-12-15T06:00:00Z"), Instant.parse("2026-12-15T08:00:00Z"));

        JsonNode timetable = calendar(WEEK).get("timetable");

        assertEquals(2, timetable.size());
        assertEquals(2, timetable.get(0).get("weekday").asInt());
        assertEquals("09:00", timetable.get(0).get("start").asString());
        assertEquals("11:00", timetable.get(0).get("end").asString());
        assertEquals("ELE 4201 Transmission Lab", timetable.get(0).get("label").asString());
        assertEquals(4, timetable.get(1).get("weekday").asInt());
        assertEquals("14:00", timetable.get(1).get("start").asString());
        assertEquals("16:00", timetable.get(1).get("end").asString());
    }

    @Test
    void aClassThatRepeatsEveryWeekAppearsOnceInALongerWindow() throws Exception {
        OperationsTestData.timetableBlock(jdbcTemplate, lab, "Weekly lab",
                Instant.parse("2026-12-08T06:00:00Z"), Instant.parse("2026-12-08T08:00:00Z"));
        OperationsTestData.timetableBlock(jdbcTemplate, lab, "Weekly lab",
                Instant.parse("2026-12-15T06:00:00Z"), Instant.parse("2026-12-15T08:00:00Z"));

        JsonNode timetable = calendar("?from=2026-12-07&to=2026-12-20").get("timetable");

        assertEquals(1, timetable.size());
    }

    @Test
    void withNoDatesItShowsTheCurrentKampalaWeek() throws Exception {
        LocalDate today = LocalDate.now(clock);
        Instant noonToday = today.atTime(12, 0).atZone(clock.getZone()).toInstant();
        UUID thisWeek = OperationsTestData.booking(jdbcTemplate, lab, noonToday, noonToday.plusSeconds(3600),
                "APPROVED", 2, "Today");
        OperationsTestData.bookingDetail(jdbcTemplate, thisWeek, "TELECOMS", "LAB", "A", "Student", null, null);
        UUID later = OperationsTestData.booking(jdbcTemplate, lab, noonToday.plusSeconds(14 * 86400L),
                noonToday.plusSeconds(14 * 86400L + 3600), "APPROVED", 2, "Two weeks on");
        OperationsTestData.bookingDetail(jdbcTemplate, later, "TELECOMS", "LAB", "A", "Student", null, null);

        JsonNode bookings = calendar("").get("bookings");

        assertEquals(1, bookings.size());
        assertEquals(thisWeek.toString(), bookings.get(0).get("id").asString());
    }

    @Test
    void aDepartmentWithNoFacilityHasAnEmptyCalendar() throws Exception {
        jdbcTemplate.update("UPDATE facilities SET department = 'moved by test' WHERE id = ?", lab);

        JsonNode calendar = calendar(WEEK);

        CALENDAR_SHAPE.check(calendar);
        assertEquals(0, calendar.get("bookings").size());
        assertEquals(0, calendar.get("timetable").size());
    }

    @Test
    void anEndDateBeforeTheStartDateIsRejected() throws Exception {
        mockMvc.perform(get("/telecoms/lab-bookings?from=2026-12-13&to=2026-12-07").with(as("OBSERVER")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("The end date must not be before the start date."));
    }

    @Test
    void aRangeOfMoreThanThreeMonthsIsRejected() throws Exception {
        mockMvc.perform(get("/telecoms/lab-bookings?from=2026-01-01&to=2026-12-31").with(as("OBSERVER")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void ninetyThreeDatesAreTooMany() throws Exception {
        mockMvc.perform(get("/telecoms/lab-bookings?from=2026-12-01&to=2027-03-03").with(as("OBSERVER")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(get("/telecoms/lab-bookings?from=2026-12-01&to=2027-03-02").with(as("OBSERVER")))
                .andExpect(status().isOk());
    }

    @Test
    void aMalformedDateIsABadRequest() throws Exception {
        mockMvc.perform(get("/telecoms/lab-bookings?from=next-week").with(as("OBSERVER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void everyRoleExceptSubmitterMayReadTheCalendar() throws Exception {
        for (String role : new String[] {"ADMINISTRATOR", "DEPARTMENT_COORDINATOR", "INNOVATION_COORDINATOR",
                "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON",
                "FINANCE_OFFICER", "OBSERVER"}) {
            mockMvc.perform(get("/telecoms/lab-bookings" + WEEK).with(as(role))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/telecoms/lab-bookings" + WEEK).with(as("SUBMITTER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/telecoms/lab-bookings" + WEEK)).andExpect(status().isUnauthorized());
    }

    private UUID labBooking(String starts, String ends, String status, int groupSize, String purpose, String bookedBy,
                            String customerType) {
        UUID id = OperationsTestData.booking(jdbcTemplate, lab, Instant.parse(starts), Instant.parse(ends), status,
                groupSize, purpose);
        OperationsTestData.bookingDetail(jdbcTemplate, id, "TELECOMS", "LAB", bookedBy, customerType, null, null);
        return id;
    }

    private JsonNode calendar(String query) throws Exception {
        return ZodShape.parse(mockMvc.perform(get("/telecoms/lab-bookings" + query).with(as("OBSERVER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String paymentOf(JsonNode bookings, UUID id) {
        for (JsonNode booking : bookings) {
            if (id.toString().equals(booking.get("id").asString())) {
                return booking.get("paymentStatus").asString();
            }
        }
        assertTrue(false, "booking " + id + " is not listed");
        return null;
    }

    private static RequestPostProcessor as(String role) {
        return user("lab-" + UUID.randomUUID() + "@test.local").roles(role);
    }
}
