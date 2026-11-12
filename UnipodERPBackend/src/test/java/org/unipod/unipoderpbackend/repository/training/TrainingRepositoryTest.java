package org.unipod.unipoderpbackend.repository.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unipod.unipoderpbackend.model.training.AttendanceStatus;
import org.unipod.unipoderpbackend.model.training.FollowUpPriority;
import org.unipod.unipoderpbackend.model.training.FollowUpStatus;
import org.unipod.unipoderpbackend.model.training.MentorshipFollowUp;
import org.unipod.unipoderpbackend.model.training.RegistrationStatus;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class TrainingRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");
    private static final Instant DAY_ONE = Instant.parse("2026-12-01T06:00:00Z");

    @Autowired
    private TrainingSessionRepository sessions;

    @Autowired
    private TrainingParticipantRepository participants;

    @Autowired
    private MentorshipFollowUpRepository followUps;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID departmentId;
    private UUID otherDepartmentId;

    @BeforeEach
    void createDepartments() {
        departmentId = insertDepartment();
        otherDepartmentId = insertDepartment();
    }

    @Test
    void listsSessionsNewestFirstWithIdAsTheTieBreak() {
        TrainingSession oldest = saveSession(departmentId, DAY_ONE, 10, 0);
        TrainingSession newest = saveSession(departmentId, DAY_ONE.plusSeconds(86_400 * 5), 10, 0);
        TrainingSession middle = saveSession(otherDepartmentId, DAY_ONE.plusSeconds(86_400), 10, 0);
        flushAndClear();

        List<UUID> order = sessions.findAllNewestFirst().stream().map(TrainingSession::getId).toList();

        assertTrue(order.indexOf(newest.getId()) < order.indexOf(middle.getId()));
        assertTrue(order.indexOf(middle.getId()) < order.indexOf(oldest.getId()));
    }

    @Test
    void filtersSessionsByDepartment() {
        TrainingSession mine = saveSession(departmentId, DAY_ONE, 10, 0);
        saveSession(otherDepartmentId, DAY_ONE, 10, 0);
        flushAndClear();

        List<TrainingSession> found = sessions.findByDepartmentNewestFirst(departmentId);

        assertEquals(List.of(mine.getId()), found.stream().map(TrainingSession::getId).toList());
    }

    @Test
    void roundTripsEverySessionField() {
        TrainingSession saved = new TrainingSession(
                UUID.randomUUID(), departmentId, "Welding safety", "Eng. Nsubuga", DAY_ONE,
                DAY_ONE.plusSeconds(10_800), 12, "Workshop 1", "PPE and fume control", 50_000, true, false, NOW);
        entityManager.persist(saved);
        flushAndClear();

        TrainingSession loaded = sessions.findById(saved.getId()).orElseThrow();

        assertEquals(departmentId, loaded.getDepartmentId());
        assertEquals("Welding safety", loaded.getTitle());
        assertEquals("Eng. Nsubuga", loaded.getFacilitator());
        assertEquals(DAY_ONE, loaded.getStartsAt());
        assertEquals(DAY_ONE.plusSeconds(10_800), loaded.getEndsAt());
        assertEquals(12, loaded.getCapacity());
        assertEquals("Workshop 1", loaded.getVenue());
        assertEquals("PPE and fume control", loaded.getDescription());
        assertEquals(50_000, loaded.getFee());
        assertTrue(loaded.isSafetyCourse());
        assertFalse(loaded.isPublished());
        assertEquals(0L, loaded.getVersion());
    }

    @Test
    void locksAnExistingSessionAndFindsNothingForAnUnknownId() {
        TrainingSession saved = saveSession(departmentId, DAY_ONE, 10, 0);
        flushAndClear();

        assertTrue(sessions.findByIdForUpdate(saved.getId()).isPresent());
        assertTrue(sessions.findByIdForUpdate(UUID.randomUUID()).isEmpty());
    }

    @Test
    void loadsActiveParticipantsOfSeveralSessionsInRegistrationOrder() {
        TrainingSession first = saveSession(departmentId, DAY_ONE, 10, 0);
        TrainingSession second = saveSession(departmentId, DAY_ONE.plusSeconds(86_400), 10, 0);
        TrainingParticipant late = saveParticipant(first, "R-LATE", 0, NOW.plusSeconds(60));
        TrainingParticipant early = saveParticipant(first, "R-EARLY", 0, NOW);
        TrainingParticipant other = saveParticipant(second, "R-OTHER", 0, NOW);
        TrainingParticipant gone = saveParticipant(first, "R-GONE", 0, NOW);
        gone.cancel(NOW.plusSeconds(120));
        flushAndClear();

        List<TrainingParticipant> found = participants.findActiveBySessionIds(
                Set.of(first.getId(), second.getId()));

        List<UUID> firstSession = found.stream()
                .filter(p -> p.getSessionId().equals(first.getId())).map(TrainingParticipant::getId).toList();
        assertEquals(List.of(early.getId(), late.getId()), firstSession);
        assertTrue(found.stream().anyMatch(p -> p.getId().equals(other.getId())));
        assertFalse(found.stream().anyMatch(p -> p.getId().equals(gone.getId())));
    }

    @Test
    void countsPendingAndConfirmedSeatsButNotCancelledOnes() {
        TrainingSession session = saveSession(departmentId, DAY_ONE, 10, 50_000);
        saveParticipant(session, "R-1", 50_000, NOW);
        TrainingParticipant paid = saveParticipant(session, "R-2", 50_000, NOW);
        paid.confirmPayment(NOW);
        TrainingParticipant cancelled = saveParticipant(session, "R-3", 50_000, NOW);
        cancelled.cancel(NOW);
        flushAndClear();

        assertEquals(2, participants.countSeats(session.getId()));
        assertEquals(0, participants.countSeats(UUID.randomUUID()));
    }

    @Test
    void recognisesARegNumberIgnoringCaseUntilItsRegistrationIsCancelled() {
        TrainingSession session = saveSession(departmentId, DAY_ONE, 10, 0);
        TrainingParticipant participant = saveParticipant(session, "2021/HD01/0001U", 0, NOW);
        flushAndClear();

        assertTrue(participants.existsActiveRegNumber(session.getId(), "2021/hd01/0001u"));
        assertFalse(participants.existsActiveRegNumber(session.getId(), "2021/HD01/9999U"));
        assertFalse(participants.existsActiveRegNumber(UUID.randomUUID(), "2021/HD01/0001U"));

        participants.findById(participant.getId()).orElseThrow().cancel(NOW);
        flushAndClear();

        assertFalse(participants.existsActiveRegNumber(session.getId(), "2021/HD01/0001U"));
    }

    @Test
    void roundTripsParticipantState() {
        TrainingSession session = saveSession(departmentId, DAY_ONE, 10, 0);
        TrainingParticipant saved = saveParticipant(session, "R-STATE", 0, NOW);
        saved.markAttendance(AttendanceStatus.PRESENT, NOW);
        saved.markPassed(true, NOW);
        flushAndClear();

        TrainingParticipant loaded = participants.findById(saved.getId()).orElseThrow();

        assertEquals(RegistrationStatus.CONFIRMED, loaded.getStatus());
        assertEquals(AttendanceStatus.PRESENT, loaded.getAttendance());
        assertTrue(loaded.isPassed());
        assertEquals("amina@students.test", loaded.getContactEmail());
        assertEquals(NOW, loaded.getRegisteredAt());
    }

    @Test
    void listsFollowUpsByScheduledDateThenId() {
        MentorshipFollowUp later = saveFollowUp(LocalDate.of(2026, 12, 20));
        MentorshipFollowUp sooner = saveFollowUp(LocalDate.of(2026, 12, 1));
        flushAndClear();

        List<UUID> order = followUps.findAllInScheduleOrder().stream().map(MentorshipFollowUp::getId).toList();

        assertTrue(order.indexOf(sooner.getId()) < order.indexOf(later.getId()));
    }

    @Test
    void roundTripsFollowUpFields() {
        MentorshipFollowUp saved = new MentorshipFollowUp(
                UUID.randomUUID(), "Prototype check-in", "Amina", "Dr Okello", LocalDate.of(2026, 12, 5),
                "Prototype", FollowUpPriority.HIGH, "Bring the demo", NOW);
        entityManager.persist(saved);
        flushAndClear();

        MentorshipFollowUp loaded = followUps.findById(saved.getId()).orElseThrow();

        assertEquals(FollowUpStatus.PENDING_FOLLOW_UP, loaded.getStatus());
        assertEquals(FollowUpPriority.HIGH, loaded.getPriority());
        assertEquals("Bring the demo", loaded.getNotes());
        assertEquals(LocalDate.of(2026, 12, 5), loaded.getScheduledDate());
        assertEquals("Dr Okello", loaded.getMentor());
    }

    private UUID insertDepartment() {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(NOW);
        jdbcTemplate.update(
                "INSERT INTO sectors (id, name, notification_email, active, created_at, updated_at) "
                        + "VALUES (?, ?, 'dept@test.local', TRUE, ?, ?)",
                id, "Training Repo Dept " + id, now, now);
        return id;
    }

    private TrainingSession saveSession(UUID department, Instant startsAt, int capacity, int fee) {
        TrainingSession session = new TrainingSession(
                UUID.randomUUID(), department, "Laser cutting", "Eng. Nsubuga", startsAt, null,
                capacity, "Lab 2", "Basics", fee, false, true, NOW);
        return entityManager.persist(session);
    }

    private TrainingParticipant saveParticipant(TrainingSession session, String regNumber, int fee, Instant at) {
        TrainingParticipant participant = new TrainingParticipant(
                UUID.randomUUID(), session.getId(), "Amina Nakato", regNumber, "Engineering",
                "amina@students.test", fee, at);
        return entityManager.persist(participant);
    }

    private MentorshipFollowUp saveFollowUp(LocalDate date) {
        return entityManager.persist(new MentorshipFollowUp(
                UUID.randomUUID(), "Check-in", "Amina", "Dr Okello", date, "Prototype", null, null, NOW));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
