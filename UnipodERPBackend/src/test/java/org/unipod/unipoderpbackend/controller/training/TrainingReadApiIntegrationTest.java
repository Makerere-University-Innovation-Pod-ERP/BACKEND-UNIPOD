package org.unipod.unipoderpbackend.controller.training;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import org.unipod.unipoderpbackend.model.training.AttendanceStatus;
import org.unipod.unipoderpbackend.model.training.FollowUpStatus;
import org.unipod.unipoderpbackend.model.training.MentorshipFollowUp;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.MentorshipFollowUpRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainingReadApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrainingSessionRepository sessions;

    @Autowired
    private TrainingParticipantRepository participants;

    @Autowired
    private MentorshipFollowUpRepository followUps;

    @Autowired
    private Clock clock;

    private UUID departmentId;

    @BeforeEach
    void seedDepartment() {
        departmentId = TrainingFixtures.department(jdbcTemplate);
    }

    @Test
    void overviewComputesKpisFromTheSessionsAndFollowUps() throws Exception {
        Instant now = clock.instant();
        // Upcoming: 10 seats, one pending, one confirmed and one cancelled registration.
        TrainingSession upcoming = save(now.plus(Duration.ofDays(3)), 10, 50_000, true);
        TrainingParticipant pending = register(upcoming, "R-PENDING", 50_000);
        TrainingParticipant confirmed = register(upcoming, "R-CONFIRMED", 50_000);
        confirmed.confirmPayment(now);
        register(upcoming, "R-CANCELLED", 50_000).cancel(now);
        // In progress: 5 seats, one confirmed.
        TrainingSession running = save(now.minus(Duration.ofMinutes(30)), 5, 0, true);
        register(running, "R-RUNNING", 0);
        // Neither counts towards seats: finished, and a draft that has not been published.
        save(now.minus(Duration.ofDays(5)), 8, 0, true);
        save(now.plus(Duration.ofDays(9)), 6, 0, false);
        jdbcTemplate.update("DELETE FROM mentorship_follow_ups");
        followUps.saveAndFlush(followUp(LocalDate.of(2026, 12, 1), false));
        followUps.saveAndFlush(followUp(LocalDate.of(2026, 12, 2), false));
        followUps.saveAndFlush(followUp(LocalDate.of(2026, 12, 3), true));

        mockMvc.perform(get("/operations/training/overview").param("departmentId", departmentId.toString())
                        .with(reader("DEPARTMENT_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingSessions").value(1))
                .andExpect(jsonPath("$.seatsFilled").value(3))
                .andExpect(jsonPath("$.totalSeats").value(15))
                .andExpect(jsonPath("$.mentorshipFollowUps").value(2))
                .andExpect(jsonPath("$.sessions", hasSize(4)))
                .andExpect(jsonPath("$.followUps", hasSize(3)))
                .andExpect(jsonPath("$.followUps[0].scheduledDate").value("2026-12-01"));
    }

    @Test
    void sessionsUseTheFrontendFieldNamesAndKampalaOffsets() throws Exception {
        // Relative to the clock so the session is always in the future; whole seconds so the JSON has no fraction.
        Instant start = clock.instant().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);
        String startText = ISO_OFFSET_DATE_TIME.format(start.atZone(clock.getZone()));
        String endText = ISO_OFFSET_DATE_TIME.format(start.plus(Duration.ofHours(2)).atZone(clock.getZone()));
        TrainingSession session = save(start, 12, 50_000, true);
        TrainingParticipant participant = register(session, "2021/HD01/0001U", 50_000);

        mockMvc.perform(get("/operations/training/overview").param("departmentId", departmentId.toString())
                        .with(reader("OBSERVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].id").value(session.getId().toString()))
                .andExpect(jsonPath("$.sessions[0].title").value(session.getTitle()))
                .andExpect(jsonPath("$.sessions[0].facilitator").value("Eng. Nsubuga"))
                .andExpect(jsonPath("$.sessions[0].date").value(startText))
                .andExpect(jsonPath("$.sessions[0].endsAt").value(endText))
                .andExpect(jsonPath("$.sessions[0].date", endsWith("+03:00")))
                .andExpect(jsonPath("$.sessions[0].enrolledCount").value(1))
                .andExpect(jsonPath("$.sessions[0].capacity").value(12))
                .andExpect(jsonPath("$.sessions[0].venue").value("Lab 2"))
                .andExpect(jsonPath("$.sessions[0].status").value("UPCOMING"))
                .andExpect(jsonPath("$.sessions[0].description").value("Basics"))
                .andExpect(jsonPath("$.sessions[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.sessions[0].fee").value(50_000))
                .andExpect(jsonPath("$.sessions[0].safetyCourse").value(false))
                .andExpect(jsonPath("$.sessions[0].published").value(true))
                .andExpect(jsonPath("$.sessions[0].participants[0].id").value(participant.getId().toString()))
                .andExpect(jsonPath("$.sessions[0].participants[0].name").value("Amina Nakato"))
                .andExpect(jsonPath("$.sessions[0].participants[0].regNumber").value("2021/HD01/0001U"))
                .andExpect(jsonPath("$.sessions[0].participants[0].department").value("Engineering"))
                .andExpect(jsonPath("$.sessions[0].participants[0].status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.sessions[0].participants[0].attendance")
                        .value(AttendanceStatus.UNMARKED.name()))
                .andExpect(jsonPath("$.sessions[0].participants[0].passed").value(false))
                .andExpect(jsonPath("$.sessions[0].participants[0].registeredAt", endsWith("+03:00")));
    }

    @Test
    void listsNewestSessionsFirstAndFiltersByDepartment() throws Exception {
        Instant now = clock.instant();
        TrainingSession older = save(now.plus(Duration.ofDays(2)), 5, 0, true);
        TrainingSession newer = save(now.plus(Duration.ofDays(20)), 5, 0, true);
        sessions.saveAndFlush(TrainingFixtures.session(
                TrainingFixtures.department(jdbcTemplate), now.plus(Duration.ofDays(30)), null, 5, 0, true));

        mockMvc.perform(get("/operations/training/overview").param("departmentId", departmentId.toString())
                        .with(reader("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions", hasSize(2)))
                .andExpect(jsonPath("$.sessions[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$.sessions[1].id").value(older.getId().toString()));
    }

    @Test
    void everyReadRoleCanReadTheOverviewAndASession() throws Exception {
        TrainingSession session = save(clock.instant().plus(Duration.ofDays(1)), 5, 0, true);

        for (String role : TrainingFixtures.READ_ROLES) {
            mockMvc.perform(get("/operations/training/overview").param("departmentId", departmentId.toString())
                            .with(reader(role)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/operations/training/sessions/{id}", session.getId()).with(reader(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void aSubmitterIsForbiddenAndAnAnonymousCallerIsUnauthorized() throws Exception {
        TrainingSession session = save(clock.instant().plus(Duration.ofDays(1)), 5, 0, true);

        mockMvc.perform(get("/operations/training/overview").with(reader("SUBMITTER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/operations/training/sessions/{id}", session.getId()).with(reader("SUBMITTER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/operations/training/overview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/operations/training/sessions/{id}", session.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOneSessionWithOnlyItsSeatHolders() throws Exception {
        TrainingSession session = save(clock.instant().plus(Duration.ofDays(1)), 5, 0, true);
        TrainingParticipant kept = register(session, "R-KEPT", 0);
        register(session, "R-REMOVED", 0).cancel(clock.instant());

        mockMvc.perform(get("/operations/training/sessions/{id}", session.getId()).with(reader("REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.getId().toString()))
                .andExpect(jsonPath("$.enrolledCount").value(1))
                .andExpect(jsonPath("$.participants", hasSize(1)))
                .andExpect(jsonPath("$.participants[0].id").value(kept.getId().toString()));
    }

    @Test
    void anUnknownSessionIsNotFound() throws Exception {
        mockMvc.perform(get("/operations/training/sessions/{id}", UUID.randomUUID()).with(reader("ADMINISTRATOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Training session not found."));
    }

    @Test
    void aMalformedIdOrDepartmentIsABadRequest() throws Exception {
        mockMvc.perform(get("/operations/training/sessions/not-a-uuid").with(reader("ADMINISTRATOR")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/operations/training/overview").param("departmentId", "nope")
                        .with(reader("ADMINISTRATOR")))
                .andExpect(status().isBadRequest());
    }

    private TrainingSession save(Instant startsAt, int capacity, int fee, boolean published) {
        return sessions.saveAndFlush(TrainingFixtures.session(departmentId, startsAt, null, capacity, fee, published));
    }

    private TrainingParticipant register(TrainingSession session, String regNumber, int fee) {
        return participants.saveAndFlush(new TrainingParticipant(
                UUID.randomUUID(), session.getId(), "Amina Nakato", regNumber, "Engineering",
                "amina@students.test", fee, clock.instant()));
    }

    private MentorshipFollowUp followUp(LocalDate date, boolean completed) {
        MentorshipFollowUp followUp = new MentorshipFollowUp(
                UUID.randomUUID(), "Check-in", "Amina", "Dr Okello", date, "Prototype", null, null, clock.instant());
        if (completed) {
            followUp.update(FollowUpStatus.COMPLETED, null, clock.instant());
        }
        return followUp;
    }

    private static RequestPostProcessor reader(String role) {
        return user("reader-" + UUID.randomUUID() + "@test.local").roles(role);
    }
}
