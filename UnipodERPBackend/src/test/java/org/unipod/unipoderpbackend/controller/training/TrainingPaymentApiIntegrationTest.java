package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.training.RegistrationStatus;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;
import org.unipod.unipoderpbackend.model.training.TrainingSession;
import org.unipod.unipoderpbackend.repository.training.TrainingParticipantRepository;
import org.unipod.unipoderpbackend.repository.training.TrainingSessionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainingPaymentApiIntegrationTest {
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
    private TrainingSession paidSession;

    @BeforeEach
    void seed() {
        departmentId = TrainingFixtures.department(jdbcTemplate);
        paidSession = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, 5, 50_000, true));
    }

    @Test
    void confirmsPaymentAndAuditsTheChange() throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(
                TrainingFixtures.participant(paidSession.getId(), "R-1", 50_000));

        mockMvc.perform(confirm(paidSession.getId(), participant.getId()).with(administrator("cashier@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paidSession.getId().toString()))
                .andExpect(jsonPath("$.enrolledCount").value(1))
                .andExpect(jsonPath("$.participants[0].status").value("CONFIRMED"));

        List<Map<String, Object>> lines = TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId());
        assertEquals(1, lines.size());
        Map<String, Object> line = lines.get(0);
        assertEquals("cashier@test.local", line.get("actor"));
        assertEquals("ADMINISTRATOR", line.get("actor_role"));
        assertEquals("training.participant.payment_confirmed", line.get("action"));
        assertEquals("training_participant", line.get("entity_type"));
        assertEquals(departmentId, line.get("department_id"));
        assertTrue(line.get("before_value").toString().contains("PENDING_PAYMENT"));
        assertTrue(line.get("after_value").toString().contains("CONFIRMED"));
        assertTrue(line.get("after_value").toString().contains("50000"));
        TrainingFixtures.assertNoPersonalData(line, "R-1");
    }

    @Test
    void afterPaymentAttendanceCanBeRecorded() throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(
                TrainingFixtures.participant(paidSession.getId(), "R-2", 50_000));
        String attendance = "{\"participantId\": \"%s\", \"attendance\": \"PRESENT\"}".formatted(participant.getId());

        mockMvc.perform(patch("/operations/training/sessions/{id}/attendance", paidSession.getId())
                        .with(administrator("cashier@test.local")).contentType(MediaType.APPLICATION_JSON).content(attendance))
                .andExpect(status().isConflict());
        mockMvc.perform(confirm(paidSession.getId(), participant.getId()).with(administrator("cashier@test.local")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/operations/training/sessions/{id}/attendance", paidSession.getId())
                        .with(administrator("cashier@test.local")).contentType(MediaType.APPLICATION_JSON).content(attendance))
                .andExpect(status().isOk());
    }

    @Test
    void paymentCannotBeConfirmedTwiceAndWritesNoSecondAuditLine() throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(
                TrainingFixtures.participant(paidSession.getId(), "R-3", 50_000));
        mockMvc.perform(confirm(paidSession.getId(), participant.getId()).with(administrator("cashier@test.local")))
                .andExpect(status().isOk());

        mockMvc.perform(confirm(paidSession.getId(), participant.getId()).with(administrator("cashier@test.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This registration is not waiting for payment."));

        assertEquals(1, TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).size());
    }

    @Test
    void aFreeRegistrationHasNothingToConfirm() throws Exception {
        TrainingSession free = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(3)), null, 5, 0, true));
        TrainingParticipant participant = participants.saveAndFlush(TrainingFixtures.participant(free.getId(), "R-4", 0));

        mockMvc.perform(confirm(free.getId(), participant.getId()).with(administrator("cashier@test.local")))
                .andExpect(status().isConflict());

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
    }

    @Test
    void aParticipantOfAnotherSessionACancelledOneOrAnUnknownOneIsNotFound() throws Exception {
        TrainingSession other = sessions.saveAndFlush(TrainingFixtures.session(
                departmentId, clock.instant().plus(Duration.ofDays(5)), null, 5, 50_000, true));
        TrainingParticipant elsewhere = participants.saveAndFlush(TrainingFixtures.participant(other.getId(), "R-5", 50_000));
        TrainingParticipant cancelled = TrainingFixtures.participant(paidSession.getId(), "R-6", 50_000);
        cancelled.cancel(clock.instant());
        participants.saveAndFlush(cancelled);

        for (UUID participantId : List.of(elsewhere.getId(), cancelled.getId(), UUID.randomUUID())) {
            mockMvc.perform(confirm(paidSession.getId(), participantId).with(administrator("cashier@test.local")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Participant not found in this session."));
        }
        mockMvc.perform(confirm(UUID.randomUUID(), elsewhere.getId()).with(administrator("cashier@test.local")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Training session not found."));

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, elsewhere.getId()).isEmpty());
        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, cancelled.getId()).isEmpty());
    }

    @Test
    void everyRoleWithoutManageAccessIsForbiddenAndNothingChanges() throws Exception {
        TrainingParticipant participant = participants.saveAndFlush(
                TrainingFixtures.participant(paidSession.getId(), "R-7", 50_000));

        for (String role : TrainingFixtures.NON_MANAGE_ROLES) {
            mockMvc.perform(confirm(paidSession.getId(), participant.getId())
                            .with(user(role.toLowerCase() + "@test.local").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(confirm(paidSession.getId(), participant.getId())).andExpect(status().isUnauthorized());

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, participant.getId()).isEmpty());
        entityManager.clear();
        assertEquals(RegistrationStatus.PENDING_PAYMENT,
                participants.findById(participant.getId()).orElseThrow().getStatus());
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        mockMvc.perform(post("/operations/training/sessions/{id}/participants/not-a-uuid/confirm-payment",
                        paidSession.getId()).with(administrator("cashier@test.local")))
                .andExpect(status().isBadRequest());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder confirm(
            UUID sessionId, UUID participantId) {
        return post("/operations/training/sessions/{id}/participants/{pid}/confirm-payment", sessionId, participantId);
    }

    private static RequestPostProcessor administrator(String email) {
        return user(email).roles("ADMINISTRATOR");
    }
}
