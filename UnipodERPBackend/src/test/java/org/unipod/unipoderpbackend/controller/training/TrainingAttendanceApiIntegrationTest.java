package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import org.unipod.unipoderpbackend.model.training.AttendanceStatus;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainingAttendanceApiIntegrationTest {
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
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, 5, 0, true, true));
    }

    @Test
    void recordsAttendanceAndAuditsTheChange() throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(session.getId(), "R-1", 0));

        mockMvc.perform(attendance(session.getId(), participant.getId(), "PRESENT").with(administrator("marker@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.getId().toString()))
                .andExpect(jsonPath("$.participants[0].id").value(participant.getId().toString()))
                .andExpect(jsonPath("$.participants[0].attendance").value("PRESENT"));

        List<Map<String, Object>> lines = TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId());
        assertEquals(1, lines.size());
        Map<String, Object> line = lines.get(0);
        assertEquals("marker@test.local", line.get("actor"));
        assertEquals("ADMINISTRATOR", line.get("actor_role"));
        assertEquals("training.attendance.recorded", line.get("action"));
        assertEquals("training_participant", line.get("entity_type"));
        assertEquals(departmentId, line.get("department_id"));
        assertTrue(line.get("before_value").toString().contains("UNMARKED"));
        assertTrue(line.get("after_value").toString().contains("PRESENT"));
        TrainingFixtures.assertNoPersonalData(line, "R-1");
    }

    @ParameterizedTest
    @EnumSource(AttendanceStatus.class)
    void acceptsEveryAttendanceValue(AttendanceStatus value) throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(session.getId(), "R-2", 0));

        mockMvc.perform(attendance(session.getId(), participant.getId(), value.name()).with(administrator("marker@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].attendance").value(value.name()));
    }

    @Test
    void aRegistrationStillAwaitingPaymentCannotBeMarked() throws Exception {
        TrainingSession paid = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, 5, 50_000, true));
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(paid.getId(), "R-3", 50_000));

        mockMvc.perform(attendance(paid.getId(), participant.getId(), "PRESENT").with(administrator("marker@test.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Attendance can only be recorded for confirmed participants."));

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
    }

    @Test
    void someoneWhoPassedMustStayPresent() throws Exception {
        TrainingParticipant participant = TrainingFixtures.participant(session.getId(), "R-4", 0);
        participant.markAttendance(AttendanceStatus.PRESENT, clock.instant());
        participant.markPassed(true, clock.instant());
        participants.saveAndFlush(participant);

        mockMvc.perform(attendance(session.getId(), participant.getId(), "ABSENT").with(administrator("marker@test.local")))
                .andExpect(status().isConflict());

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
    }

    @Test
    void aParticipantOfAnotherSessionACancelledOneOrAnUnknownOneIsNotFound() throws Exception {
        TrainingSession other = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(5)), null, 5, 0, true));
        TrainingParticipant elsewhere = participants.saveAndFlush(TrainingFixtures.participant(other.getId(), "R-5", 0));
        TrainingParticipant cancelled = TrainingFixtures.participant(session.getId(), "R-6", 0);
        cancelled.cancel(clock.instant());
        participants.saveAndFlush(cancelled);

        for (UUID participantId : List.of(elsewhere.getId(), cancelled.getId(), UUID.randomUUID())) {
            mockMvc.perform(attendance(session.getId(), participantId, "PRESENT").with(administrator("marker@test.local")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Participant not found in this session."));
        }
        mockMvc.perform(attendance(UUID.randomUUID(), elsewhere.getId(), "PRESENT").with(administrator("marker@test.local")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Training session not found."));

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, elsewhere.getId()).isEmpty());
        assertEquals(0, TrainingFixtures.auditLines(entityManager, jdbcTemplate, cancelled.getId()).size());
    }

    @Test
    void everyRoleWithoutManageAccessIsForbiddenAndNothingChanges() throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(session.getId(), "R-7", 0));

        for (String role : TrainingFixtures.NON_MANAGE_ROLES) {
            mockMvc.perform(attendance(session.getId(), participant.getId(), "PRESENT")
                            .with(user(role.toLowerCase() + "@test.local").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(attendance(session.getId(), participant.getId(), "PRESENT")).andExpect(status().isUnauthorized());

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
        entityManager.clear();
        assertEquals(AttendanceStatus.UNMARKED, participants.findById(participant.getId()).orElseThrow().getAttendance());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"attendance\": \"PRESENT\"}",
            "{\"participantId\": \"11111111-1111-1111-1111-111111111111\"}",
            "{\"participantId\": null, \"attendance\": \"PRESENT\"}"})
    void rejectsABodyMissingAFieldWithValidationErrors(String json) throws Exception {
        mockMvc.perform(patch("/operations/training/sessions/{id}/attendance", session.getId())
                        .with(administrator("marker@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"participantId\": \"11111111-1111-1111-1111-111111111111\", \"attendance\": \"LATE\"}",
            "{\"participantId\": \"not-a-uuid\", \"attendance\": \"PRESENT\"}",
            "{\"participantId\": \"11111111-1111-1111-1111-111111111111\", \"attendance\": \"PRESENT\", \"note\": \"x\"}"})
    void rejectsAMalformedBodyWithBadRequest(String json) throws Exception {
        mockMvc.perform(patch("/operations/training/sessions/{id}/attendance", session.getId())
                        .with(administrator("marker@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    private static MockHttpServletRequestBuilder attendance(UUID sessionId, UUID participantId, String value) {
        return patch("/operations/training/sessions/{id}/attendance", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"participantId\": \"%s\", \"attendance\": \"%s\"}".formatted(participantId, value));
    }

    private static RequestPostProcessor administrator(String email) {
        return user(email).roles("ADMINISTRATOR");
    }
}
