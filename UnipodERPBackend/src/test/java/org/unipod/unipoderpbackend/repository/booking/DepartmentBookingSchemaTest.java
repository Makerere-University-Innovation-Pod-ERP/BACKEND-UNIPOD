package org.unipod.unipoderpbackend.repository.booking;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class DepartmentBookingSchemaTest {
    private static final Instant START = Instant.parse("2026-12-01T06:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID facilityId;
    private UUID bookingId;

    @BeforeEach
    void seed() {
        facilityId = UUID.randomUUID();
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO facilities (id, name, capacity, status, department, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 20, 'AVAILABLE', 'TELECOMS', 'seed@test.local', ?, ?)",
                facilityId, "Schema lab " + facilityId, now, now);
        bookingId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO bookings (id, reference, facility_id, requester_id, purpose, starts_at, ends_at, "
                        + "attendees, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'seed@test.local', 'Schema test', ?, ?, 4, 'APPROVED', ?, ?)",
                bookingId, "SCH-" + bookingId.toString().substring(0, 8), facilityId,
                Timestamp.from(START), Timestamp.from(START.plusSeconds(3600)), now, now);
    }

    @Test
    void acceptsALabDetailRowAndAWorkshopAttendee() {
        insertDetail(bookingId, "TELECOMS", "LAB", null);

        UUID textileBooking = insertBooking();
        insertDetail(textileBooking, "TEXTILE", "TRAINING", 12);
        assertDoesNotThrow(() -> insertAttendee(textileBooking, "Brian Kato"));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM booking_attendees WHERE booking_id = ?", Integer.class, textileBooking));
        assertEquals(false, jdbcTemplate.queryForObject(
                "SELECT attended FROM booking_attendees WHERE booking_id = ?", Boolean.class, textileBooking));
    }

    @Test
    void rejectsADetailRowForAnUnknownBooking() {
        assertViolates("fk_department_booking_details_booking",
                () -> insertDetail(UUID.randomUUID(), "TELECOMS", "LAB", null));
    }

    // Each violation gets its own test: Postgres aborts the surrounding transaction after the first one.
    @Test
    void rejectsAnUnknownDepartment() {
        assertViolates("ck_department_booking_details_department",
                () -> insertDetail(bookingId, "CNC", "LAB", null));
    }

    @Test
    void rejectsAnUnknownKind() {
        assertViolates("ck_department_booking_details_kind",
                () -> insertDetail(bookingId, "TEXTILE", "SEMINAR", null));
    }

    @Test
    void aTelecomsBookingCannotBeAWorkshop() {
        assertViolates("ck_department_booking_details_kind_matches_department",
                () -> insertDetail(bookingId, "TELECOMS", "WORKSHOP", null));
    }

    @Test
    void aTextileBookingCannotBeALabBooking() {
        assertViolates("ck_department_booking_details_kind_matches_department",
                () -> insertDetail(bookingId, "TEXTILE", "LAB", null));
    }

    @Test
    void aWorkshopCannotCarryACapacity() {
        assertViolates("ck_department_booking_details_capacity_for_training",
                () -> insertDetail(bookingId, "TEXTILE", "WORKSHOP", 10));
    }

    @Test
    void aCapacityMustBePositive() {
        assertViolates("ck_department_booking_details_capacity",
                () -> insertDetail(bookingId, "TEXTILE", "TRAINING", 0));
    }

    @Test
    void aCancellationReasonNeedsACancellationTime() {
        insertDetail(bookingId, "TELECOMS", "LAB", null);
        assertViolates("ck_department_booking_details_cancellation", () -> jdbcTemplate.update(
                "UPDATE department_booking_details SET cancel_reason = 'Rain' WHERE booking_id = ?", bookingId));
    }

    @Test
    void aCancellationTimeNeedsACancellationReason() {
        insertDetail(bookingId, "TELECOMS", "LAB", null);
        assertViolates("ck_department_booking_details_cancellation", () -> jdbcTemplate.update(
                "UPDATE department_booking_details SET cancelled_at = now() WHERE booking_id = ?", bookingId));
    }

    @Test
    void aWorkshopWithoutACapacityIsAccepted() {
        assertDoesNotThrow(() -> insertDetail(bookingId, "TEXTILE", "WORKSHOP", null));
    }

    @Test
    void rejectsABlankAttendeeName() {
        insertDetail(bookingId, "TEXTILE", "TRAINING", 5);
        assertViolates("ck_booking_attendees_name", () -> insertAttendee(bookingId, "   "));
    }

    @Test
    void rejectsAnAttendeeOnABookingThatIsNotADepartmentBooking() {
        assertViolates("fk_booking_attendees_booking", () -> insertAttendee(bookingId, "Aisha"));
    }

    @Test
    void deletingABookingRemovesItsDetailAndAttendees() {
        insertDetail(bookingId, "TEXTILE", "TRAINING", 5);
        insertAttendee(bookingId, "Moses");

        jdbcTemplate.update("DELETE FROM bookings WHERE id = ?", bookingId);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM department_booking_details WHERE booking_id = ?", Integer.class, bookingId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM booking_attendees WHERE booking_id = ?", Integer.class, bookingId));
    }

    @Test
    void mediaRequestsGainTheBoardColumns() {
        UUID id = insertMediaRequest();

        assertEquals(facilityId, jdbcTemplate.queryForObject(
                "SELECT studio_id FROM media_requests WHERE id = ?", UUID.class, id));
        assertEquals("Producer", jdbcTemplate.queryForObject(
                "SELECT assignee FROM media_requests WHERE id = ?", String.class, id));
    }

    @Test
    void aMediaRequestRejectsABlankAssignee() {
        UUID id = insertMediaRequest();
        assertViolates("ck_media_requests_assignee", () -> jdbcTemplate.update(
                "UPDATE media_requests SET assignee = '  ' WHERE id = ?", id));
    }

    @Test
    void aMediaRequestRejectsAnEmptyDescription() {
        UUID id = insertMediaRequest();
        assertViolates("ck_media_requests_description", () -> jdbcTemplate.update(
                "UPDATE media_requests SET description = '' WHERE id = ?", id));
    }

    @Test
    void aMediaRequestRejectsAnUnknownStudio() {
        UUID id = insertMediaRequest();
        assertViolates("fk_media_requests_studio", () -> jdbcTemplate.update(
                "UPDATE media_requests SET studio_id = ? WHERE id = ?", UUID.randomUUID(), id));
    }

    private UUID insertMediaRequest() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO media_requests (id, code, submitter_id, title, status, created_at, updated_at, "
                        + "studio_id, assignee, scheduled_at, description) "
                        + "VALUES (?, ?, 'a@test.local', 'Podcast', 'REQUESTED', ?, ?, ?, 'Producer', ?, 'Episode')",
                id, "MRQ-SCHEMA-" + id.toString().substring(0, 8), now, now, facilityId, now);
        return id;
    }

    private UUID insertBooking() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO bookings (id, reference, facility_id, requester_id, purpose, starts_at, ends_at, "
                        + "attendees, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'seed@test.local', 'Schema test', ?, ?, 4, 'APPROVED', ?, ?)",
                id, "SCH-" + id.toString().substring(0, 8), facilityId,
                Timestamp.from(START.plusSeconds(7200)), Timestamp.from(START.plusSeconds(10800)), now, now);
        return id;
    }

    private void insertDetail(UUID booking, String department, String kind, Integer capacity) {
        jdbcTemplate.update(
                "INSERT INTO department_booking_details (booking_id, department, kind, booked_by, capacity, created_at) "
                        + "VALUES (?, ?, ?, 'seed@test.local', ?, ?)",
                booking, department, kind, capacity, Timestamp.from(START));
    }

    private void insertAttendee(UUID booking, String name) {
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO booking_attendees (id, booking_id, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), booking, name, now, now);
    }

    private static void assertViolates(String constraint, Runnable statement) {
        DataIntegrityViolationException thrown =
                assertThrows(DataIntegrityViolationException.class, statement::run);
        assertTrue(String.valueOf(thrown.getMostSpecificCause().getMessage()).contains(constraint),
                () -> "expected a violation of " + constraint + " but got: "
                        + thrown.getMostSpecificCause().getMessage());
    }
}
