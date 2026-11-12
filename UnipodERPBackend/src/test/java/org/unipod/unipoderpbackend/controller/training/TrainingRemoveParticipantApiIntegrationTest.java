package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
import org.unipod.unipoderpbackend.model.training.RegistrationStatus;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainingRemoveParticipantApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TrainingSessionRepository sessions;

    @Autowired
    private TrainingParticipantRepository participants;

    @Autowired
    private Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID departmentId;
    private TrainingSession session;

    @BeforeEach
    void seed() {
        departmentId = TrainingFixtures.department(jdbcTemplate);
        session = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, 2, 0, true));
    }

    @Test
    void removesTheRegistrationFreesTheSeatAndAuditsIt() throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(session.getId(), "R-1", 0));

        mockMvc.perform(delete(path(participant)).with(administrator("remover@test.local")))
                .andExpect(status().isNoContent());

        // Flush first: clearing would drop the audit line that is still a pending insert.
        entityManager.flush();
        entityManager.clear();
        assertEquals(RegistrationStatus.CANCELLED, participants.findById(participant.getId()).orElseThrow().getStatus());
        assertEquals(0, participants.countSeats(session.getId()));
        assertFalse(participants.existsActiveRegNumber(session.getId(), "R-1"));
        mockMvc.perform(get("/operations/training/sessions/{id}", session.getId()).with(administrator("remover@test.local")))
                .andExpect(jsonPath("$.enrolledCount").value(0))
                .andExpect(jsonPath("$.participants").isEmpty());

        List<Map<String, Object>> lines = TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId());
        assertEquals(1, lines.size());
        Map<String, Object> line = lines.get(0);
        assertEquals("remover@test.local", line.get("actor"));
        assertEquals("ADMINISTRATOR", line.get("actor_role"));
        assertEquals("training.participant.removed", line.get("action"));
        assertEquals("training_participant", line.get("entity_type"));
        assertEquals(departmentId, line.get("department_id"));
        assertTrue(line.get("before_value").toString().contains("CONFIRMED"));
        assertTrue(line.get("after_value").toString().contains("CANCELLED"));
        TrainingFixtures.assertNoPersonalData(line, "R-1");
    }

    @Test
    void aRemovedRegistrationWaitingForPaymentIsRemovedToo() throws Exception {
        TrainingSession paid = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, 2, 50_000, true));
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(paid.getId(), "R-2", 50_000));

        mockMvc.perform(delete("/operations/training/sessions/{id}/participants/{pid}", paid.getId(), participant.getId())
                        .with(administrator("remover@test.local")))
                .andExpect(status().isNoContent());

        assertEquals(0, participants.countSeats(paid.getId()));
    }

    @Test
    void someoneWhoPassedCannotBeRemoved() throws Exception {
        TrainingSession safety = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, 2, 0, true, true));
        TrainingParticipant participant = TrainingFixtures.participant(safety.getId(), "R-3", 0);
        participant.markAttendance(AttendanceStatus.PRESENT, clock.instant());
        participant.markPassed(true, clock.instant());
        participants.saveAndFlush(participant);

        mockMvc.perform(delete("/operations/training/sessions/{id}/participants/{pid}", safety.getId(), participant.getId())
                        .with(administrator("remover@test.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A participant who has passed cannot be removed."));

        assertEquals(1, participants.countSeats(safety.getId()));
        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
    }

    @Test
    void aParticipantOfAnotherSessionIsNotFound() throws Exception {
        TrainingSession other = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(5)), null, 2, 0, true));
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(other.getId(), "R-4", 0));

        mockMvc.perform(delete(path(participant)).with(administrator("remover@test.local")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Participant not found in this session."));

        assertEquals(1, participants.countSeats(other.getId()));
        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
    }

    @Test
    void anUnknownParticipantOrSessionIsNotFound() throws Exception {
        mockMvc.perform(delete("/operations/training/sessions/{id}/participants/{pid}", session.getId(), UUID.randomUUID())
                        .with(administrator("remover@test.local")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/operations/training/sessions/{id}/participants/{pid}", UUID.randomUUID(), UUID.randomUUID())
                        .with(administrator("remover@test.local")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Training session not found."));
    }

    @Test
    void aRegistrationAlreadyRemovedCountsAsGone() throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(session.getId(), "R-5", 0));
        mockMvc.perform(delete(path(participant)).with(administrator("remover@test.local")))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete(path(participant)).with(administrator("remover@test.local")))
                .andExpect(status().isNotFound());

        assertEquals(1, TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).size());
    }

    @Test
    void everyRoleWithoutManageAccessIsForbiddenAndNothingChanges() throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(session.getId(), "R-6", 0));

        for (String role : TrainingFixtures.NON_MANAGE_ROLES) {
            mockMvc.perform(delete(path(participant)).with(user(role.toLowerCase() + "@test.local").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(delete(path(participant))).andExpect(status().isUnauthorized());

        assertEquals(1, participants.countSeats(session.getId()));
        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        mockMvc.perform(delete("/operations/training/sessions/{id}/participants/not-a-uuid", session.getId())
                        .with(administrator("remover@test.local")))
                .andExpect(status().isBadRequest());
    }

    private String path(TrainingParticipant participant) {
        return "/operations/training/sessions/" + session.getId() + "/participants/" + participant.getId();
    }

    private static RequestPostProcessor administrator(String email) {
        return user(email).roles("ADMINISTRATOR");
    }
}
