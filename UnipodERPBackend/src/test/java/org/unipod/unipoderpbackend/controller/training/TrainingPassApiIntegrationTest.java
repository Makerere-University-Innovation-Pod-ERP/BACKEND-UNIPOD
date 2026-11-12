package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class TrainingPassApiIntegrationTest {
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
    private TrainingSession safetyCourse;

    @BeforeEach
    void seed() {
        departmentId = TrainingFixtures.department(jdbcTemplate);
        safetyCourse = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, 5, 0, true, true));
    }

    @Test
    void recordsAPassForAPresentParticipantAndAuditsIt() throws Exception {
        TrainingParticipant participant = present(safetyCourse, "R-1");

        mockMvc.perform(pass(safetyCourse.getId(), participant.getId()).with(administrator("assessor@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(safetyCourse.getId().toString()))
                .andExpect(jsonPath("$.participants[0].passed").value(true))
                .andExpect(jsonPath("$.participants[0].attendance").value("PRESENT"));

        List<Map<String, Object>> lines = TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId());
        assertEquals(1, lines.size());
        Map<String, Object> line = lines.get(0);
        assertEquals("assessor@test.local", line.get("actor"));
        assertEquals("ADMINISTRATOR", line.get("actor_role"));
        assertEquals("training.participant.passed", line.get("action"));
        assertEquals("training_participant", line.get("entity_type"));
        assertEquals(departmentId, line.get("department_id"));
        assertTrue(line.get("before_value").toString().contains("false"));
        assertTrue(line.get("after_value").toString().contains("true"));
        TrainingFixtures.assertNoPersonalData(line, "R-1");
    }

    @Test
    void onlySafetyCoursesRecordAPass() throws Exception {
        TrainingSession ordinary = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, 5, 0, true));
        TrainingParticipant participant = present(ordinary, "R-2");

        mockMvc.perform(pass(ordinary.getId(), participant.getId()).with(administrator("assessor@test.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only safety courses record a pass."));

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = AttendanceStatus.class, names = {"UNMARKED", "ABSENT", "EXCUSED"})
    void aParticipantNotMarkedPresentCannotPass(AttendanceStatus attendance) throws Exception {
        TrainingParticipant participant = TrainingFixtures.participant(safetyCourse.getId(), "R-3", 0);
        participant.markAttendance(attendance, clock.instant());
        participants.saveAndFlush(participant);

        mockMvc.perform(pass(safetyCourse.getId(), participant.getId()).with(administrator("assessor@test.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A participant must be marked present before passing."));

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
    }

    @Test
    void aPassCannotBeRecordedTwice() throws Exception {
        TrainingParticipant participant = present(safetyCourse, "R-4");
        mockMvc.perform(pass(safetyCourse.getId(), participant.getId()).with(administrator("assessor@test.local")))
                .andExpect(status().isOk());

        mockMvc.perform(pass(safetyCourse.getId(), participant.getId()).with(administrator("assessor@test.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This participant has already passed."));

        assertEquals(1, TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).size());
    }

    @Test
    void afterAPassTheParticipantCannotBeRemoved() throws Exception {
        TrainingParticipant participant = present(safetyCourse, "R-5");
        mockMvc.perform(pass(safetyCourse.getId(), participant.getId()).with(administrator("assessor@test.local")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/operations/training/sessions/{id}/participants/{pid}",
                        safetyCourse.getId(), participant.getId()).with(administrator("assessor@test.local")))
                .andExpect(status().isConflict());

        assertEquals(1, participants.countSeats(safetyCourse.getId()));
    }

    @Test
    void aParticipantOfAnotherSessionACancelledOneOrAnUnknownOneIsNotFound() throws Exception {
        TrainingSession other = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(5)), null, 5, 0, true, true));
        TrainingParticipant elsewhere = present(other, "R-6");
        TrainingParticipant cancelled = TrainingFixtures.participant(safetyCourse.getId(), "R-7", 0);
        cancelled.cancel(clock.instant());
        participants.saveAndFlush(cancelled);

        for (UUID participantId : List.of(elsewhere.getId(), cancelled.getId(), UUID.randomUUID())) {
            mockMvc.perform(pass(safetyCourse.getId(), participantId).with(administrator("assessor@test.local")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Participant not found in this session."));
        }
        mockMvc.perform(pass(UUID.randomUUID(), elsewhere.getId()).with(administrator("assessor@test.local")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Training session not found."));

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, elsewhere.getId()).isEmpty());
        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, cancelled.getId()).isEmpty());
    }

    @Test
    void everyRoleWithoutManageAccessIsForbiddenAndNothingChanges() throws Exception {
        TrainingParticipant participant = present(safetyCourse, "R-8");

        for (String role : TrainingFixtures.NON_MANAGE_ROLES) {
            mockMvc.perform(pass(safetyCourse.getId(), participant.getId())
                            .with(user(role.toLowerCase() + "@test.local").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(pass(safetyCourse.getId(), participant.getId())).andExpect(status().isUnauthorized());

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
        entityManager.clear();
        assertFalse(participants.findById(participant.getId()).orElseThrow().isPassed());
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        mockMvc.perform(post("/operations/training/sessions/{id}/participants/not-a-uuid/pass", safetyCourse.getId())
                        .with(administrator("assessor@test.local")))
                .andExpect(status().isBadRequest());
    }

    private TrainingParticipant present(TrainingSession session, String regNumber) {
        TrainingParticipant participant = TrainingFixtures.participant(session.getId(), regNumber, 0);
        participant.markAttendance(AttendanceStatus.PRESENT, clock.instant());
        return participants.saveAndFlush(participant);
    }

    private static MockHttpServletRequestBuilder pass(UUID sessionId, UUID participantId) {
        return post("/operations/training/sessions/{id}/participants/{pid}/pass", sessionId, participantId);
    }

    private static RequestPostProcessor administrator(String email) {
        return user(email).roles("ADMINISTRATOR");
    }
}
