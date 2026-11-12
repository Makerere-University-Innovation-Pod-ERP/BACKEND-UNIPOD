package org.unipod.unipoderpbackend.repository.training;

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
class TrainingSchemaTest {
    private static final Instant START = Instant.parse("2026-12-01T06:00:00Z");
    private static final Instant END = START.plusSeconds(7200);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID departmentId;

    @BeforeEach
    void createDepartment() {
        departmentId = UUID.randomUUID();
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO sectors (id, name, notification_email, active, created_at, updated_at) "
                        + "VALUES (?, ?, 'dept@test.local', TRUE, ?, ?)",
                departmentId, "Training Schema Dept " + departmentId, now, now);
    }

    @Test
    void appliesDefaultsToASessionWithOnlyRequiredColumns() {
        // No fee, safety_course, published or description in the insert: the columns must fall back to their defaults.
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO training_sessions (id, department_id, title, facilitator, starts_at, ends_at, "
                        + "capacity, venue, created_at, updated_at) "
                        + "VALUES (?, ?, 'Laser cutting basics', 'Eng. Nsubuga', ?, ?, 20, 'Lab 2', ?, ?)",
                id, departmentId, Timestamp.from(START), Timestamp.from(END), now, now);

        var row = jdbcTemplate.queryForMap(
                "SELECT fee, safety_course, published, version, description FROM training_sessions WHERE id = ?", id);
        assertEquals(0, row.get("fee"));
        assertEquals(false, row.get("safety_course"));
        assertEquals(true, row.get("published"));
        assertEquals(0L, row.get("version"));
        assertEquals("", row.get("description"));
    }

    @Test
    void rejectsASessionForAnUnknownDepartment() {
        assertViolates("fk_training_sessions_department",
                () -> insertSession(UUID.randomUUID(), START, END, 20, 0));
    }

    @Test
    void rejectsASessionThatEndsBeforeItStarts() {
        assertViolates("ck_training_sessions_window",
                () -> insertSession(departmentId, START, START, 20, 0));
    }

    @Test
    void rejectsZeroCapacity() {
        assertViolates("ck_training_sessions_capacity",
                () -> insertSession(departmentId, START, END, 0, 0));
    }

    @Test
    void rejectsANegativeFee() {
        assertViolates("ck_training_sessions_fee",
                () -> insertSession(departmentId, START, END, 20, -1));
    }

    @Test
    void rejectsABlankTitle() {
        assertViolates("ck_training_sessions_title", () -> jdbcTemplate.update(
                "INSERT INTO training_sessions (id, department_id, title, facilitator, starts_at, ends_at, "
                        + "capacity, venue, created_at, updated_at) VALUES (?, ?, '  ', 'F', ?, ?, 5, 'V', ?, ?)",
                UUID.randomUUID(), departmentId, Timestamp.from(START), Timestamp.from(END),
                Timestamp.from(START), Timestamp.from(START)));
    }

    @Test
    void rejectsTheSameRegNumberTwiceInOneSessionIgnoringCase() {
        UUID session = insertSession(departmentId, START, END, 20, 0);
        insertParticipant(session, "2021/HD01/0001U", "CONFIRMED", "UNMARKED", false);

        assertViolates("uq_training_participants_session_reg_number",
                () -> insertParticipant(session, "2021/hd01/0001u", "PENDING_PAYMENT", "UNMARKED", false));
    }

    @Test
    void allowsTheSameRegNumberAgainOnceTheFirstRegistrationIsCancelled() {
        UUID session = insertSession(departmentId, START, END, 20, 0);
        insertParticipant(session, "2021/HD01/0002U", "CANCELLED", "UNMARKED", false);

        assertDoesNotThrow(() -> insertParticipant(session, "2021/HD01/0002U", "CONFIRMED", "UNMARKED", false));
    }

    @Test
    void allowsTheSameRegNumberInAnotherSession() {
        UUID first = insertSession(departmentId, START, END, 20, 0);
        UUID second = insertSession(departmentId, START.plusSeconds(86400), END.plusSeconds(86400), 20, 0);
        insertParticipant(first, "2021/HD01/0003U", "CONFIRMED", "UNMARKED", false);

        assertDoesNotThrow(() -> insertParticipant(second, "2021/HD01/0003U", "CONFIRMED", "UNMARKED", false));
    }

    @Test
    void rejectsAnUnknownRegistrationStatus() {
        UUID session = insertSession(departmentId, START, END, 20, 0);

        assertViolates("ck_training_participants_status",
                () -> insertParticipant(session, "2021/HD01/0004U", "WAITLISTED", "UNMARKED", false));
    }

    @Test
    void rejectsAnUnknownAttendanceValue() {
        UUID session = insertSession(departmentId, START, END, 20, 0);

        assertViolates("ck_training_participants_attendance",
                () -> insertParticipant(session, "2021/HD01/0005U", "CONFIRMED", "LATE", false));
    }

    @Test
    void rejectsAPassForSomeoneWhoWasNotPresent() {
        UUID session = insertSession(departmentId, START, END, 20, 0);

        assertViolates("ck_training_participants_passed_present",
                () -> insertParticipant(session, "2021/HD01/0006U", "CONFIRMED", "ABSENT", true));
    }

    @Test
    void appliesFollowUpDefaults() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO mentorship_follow_ups (id, title, mentee, mentor, scheduled_date, focus, "
                        + "created_at, updated_at) VALUES (?, 'Check-in', 'Amina', 'Dr Okello', DATE '2026-12-05', "
                        + "'Prototype', ?, ?)", id, now, now);

        var row = jdbcTemplate.queryForMap(
                "SELECT status, priority, notes FROM mentorship_follow_ups WHERE id = ?", id);
        assertEquals("PENDING_FOLLOW_UP", row.get("status"));
        assertEquals("NORMAL", row.get("priority"));
        assertEquals("", row.get("notes"));
    }

    @Test
    void rejectsAnUnknownFollowUpStatus() {
        assertViolates("ck_mentorship_follow_ups_status", () -> insertFollowUp("DONE", "NORMAL"));
    }

    @Test
    void rejectsAnUnknownFollowUpPriority() {
        assertViolates("ck_mentorship_follow_ups_priority", () -> insertFollowUp("SCHEDULED", "LOW"));
    }

    private void assertViolates(String constraint, Runnable statement) {
        DataIntegrityViolationException failure =
                assertThrows(DataIntegrityViolationException.class, statement::run);
        assertTrue(failure.getMessage().contains(constraint), failure.getMessage());
    }

    private UUID insertSession(UUID department, Instant startsAt, Instant endsAt, int capacity, int fee) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO training_sessions (id, department_id, title, facilitator, starts_at, ends_at, "
                        + "capacity, venue, fee, created_at, updated_at) "
                        + "VALUES (?, ?, 'Laser cutting basics', 'Eng. Nsubuga', ?, ?, ?, 'Lab 2', ?, ?, ?)",
                id, department, Timestamp.from(startsAt), Timestamp.from(endsAt), capacity, fee, now, now);
        return id;
    }

    private void insertParticipant(UUID session, String regNumber, String status, String attendance, boolean passed) {
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO training_participants (id, session_id, name, reg_number, department, status, "
                        + "attendance, passed, registered_at, updated_at) "
                        + "VALUES (?, ?, 'Amina Nakato', ?, 'Engineering', ?, ?, ?, ?, ?)",
                UUID.randomUUID(), session, regNumber, status, attendance, passed, now, now);
    }

    private void insertFollowUp(String status, String priority) {
        Timestamp now = Timestamp.from(START);
        jdbcTemplate.update(
                "INSERT INTO mentorship_follow_ups (id, title, mentee, mentor, scheduled_date, focus, status, "
                        + "priority, created_at, updated_at) "
                        + "VALUES (?, 'Check-in', 'Amina', 'Dr Okello', DATE '2026-12-05', 'Prototype', ?, ?, ?, ?)",
                UUID.randomUUID(), status, priority, now, now);
    }
}
