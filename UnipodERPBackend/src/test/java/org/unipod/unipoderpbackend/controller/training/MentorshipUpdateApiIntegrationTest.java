package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.unipod.unipoderpbackend.model.training.FollowUpStatus;
import org.unipod.unipoderpbackend.model.training.MentorshipFollowUp;
import org.unipod.unipoderpbackend.repository.training.MentorshipFollowUpRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MentorshipUpdateApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MentorshipFollowUpRepository followUps;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void movesTheStatusAndAuditsItWithoutTheNotesText() throws Exception {
        MentorshipFollowUp followUp = saved("Initial notes");

        mockMvc.perform(update(followUp.getId(), "{\"status\": \"ACTION_REQUIRED\", \"notes\": \"Secret new notes\"}")
                        .with(administrator("mentor-admin@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(followUp.getId().toString()))
                .andExpect(jsonPath("$.status").value("ACTION_REQUIRED"))
                .andExpect(jsonPath("$.notes").value("Secret new notes"))
                .andExpect(jsonPath("$.mentee").value("Amina Nakato"));

        List<Map<String, Object>> lines = TrainingFixtures.auditLines(entityManager, jdbcTemplate, followUp.getId());
        assertEquals(1, lines.size());
        Map<String, Object> line = lines.get(0);
        assertEquals("mentor-admin@test.local", line.get("actor"));
        assertEquals("ADMINISTRATOR", line.get("actor_role"));
        assertEquals("training.follow_up.updated", line.get("action"));
        assertEquals("mentorship_follow_up", line.get("entity_type"));
        assertTrue(line.get("before_value").toString().contains("PENDING_FOLLOW_UP"));
        assertTrue(line.get("after_value").toString().contains("ACTION_REQUIRED"));
        assertTrue(line.get("after_value").toString().contains("\"notesChanged\": true"));
        String text = line.get("before_value") + " " + line.get("after_value");
        for (String personal : List.of("Amina", "Nakato", "Okello", "Secret new notes", "Initial notes")) {
            assertFalse(text.contains(personal), "audit line leaks: " + personal);
        }
    }

    @Test
    void aStatusOnlyUpdateKeepsTheNotesAndRecordsThemAsUnchanged() throws Exception {
        MentorshipFollowUp followUp = saved("Keep these");

        mockMvc.perform(update(followUp.getId(), "{\"status\": \"SCHEDULED\"}").with(administrator("mentor-admin@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.notes").value("Keep these"));

        Map<String, Object> line = TrainingFixtures.auditLines(entityManager, jdbcTemplate, followUp.getId()).get(0);
        assertTrue(line.get("after_value").toString().contains("\"notesChanged\": false"));
    }

    @Test
    void sendingTheSameNotesIsNotAChange() throws Exception {
        MentorshipFollowUp followUp = saved("Same notes");

        mockMvc.perform(update(followUp.getId(), "{\"status\": \"SCHEDULED\", \"notes\": \"Same notes\"}")
                        .with(administrator("mentor-admin@test.local")))
                .andExpect(status().isOk());

        Map<String, Object> line = TrainingFixtures.auditLines(entityManager, jdbcTemplate, followUp.getId()).get(0);
        assertTrue(line.get("after_value").toString().contains("\"notesChanged\": false"));
    }

    @Test
    void blankNotesClearThem() throws Exception {
        MentorshipFollowUp followUp = saved("To be cleared");

        mockMvc.perform(update(followUp.getId(), "{\"status\": \"SCHEDULED\", \"notes\": \"  \"}")
                        .with(administrator("mentor-admin@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value(""));
    }

    @Test
    void completingIsAllowedAndThenTheFollowUpIsClosedForGood() throws Exception {
        MentorshipFollowUp followUp = saved("Open");
        mockMvc.perform(update(followUp.getId(), "{\"status\": \"COMPLETED\", \"notes\": \"Done\"}")
                        .with(administrator("mentor-admin@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(update(followUp.getId(), "{\"status\": \"SCHEDULED\", \"notes\": \"Reopen\"}")
                        .with(administrator("mentor-admin@test.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This follow-up is already completed."));

        assertEquals(1, TrainingFixtures.auditLines(entityManager, jdbcTemplate, followUp.getId()).size());
        entityManager.clear();
        MentorshipFollowUp reloaded = followUps.findById(followUp.getId()).orElseThrow();
        assertEquals(FollowUpStatus.COMPLETED, reloaded.getStatus());
        assertEquals("Done", reloaded.getNotes());
    }

    @Test
    void anEditBasedOnAStaleVersionIsAConflictAndWritesNothing() throws Exception {
        MentorshipFollowUp followUp = saved("Open");
        // Someone else saves first: the row's version moves on while this request still holds the old one.
        jdbcTemplate.update("UPDATE mentorship_follow_ups SET version = version + 1 WHERE id = ?", followUp.getId());

        mockMvc.perform(update(followUp.getId(), "{\"status\": \"SCHEDULED\", \"notes\": \"Late edit\"}")
                        .with(administrator("mentor-admin@test.local")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("This follow-up was changed by someone else. Reload it and try again."));

        // The failed update left the entity dirty in this shared test transaction (production rolls back);
        // drop it so the checks below do not retry the same stale write.
        entityManager.clear();
        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, followUp.getId()).isEmpty());
        String notes = jdbcTemplate.queryForObject(
                "SELECT notes FROM mentorship_follow_ups WHERE id = ?", String.class, followUp.getId());
        assertEquals("Open", notes);
    }

    @ParameterizedTest
    @EnumSource(FollowUpStatus.class)
    void anOpenFollowUpCanMoveToAnyStatus(FollowUpStatus target) throws Exception {
        MentorshipFollowUp followUp = saved("Open");

        mockMvc.perform(update(followUp.getId(), "{\"status\": \"%s\"}".formatted(target))
                        .with(administrator("mentor-admin@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(target.name()));
    }

    @Test
    void anUnknownFollowUpIsNotFound() throws Exception {
        mockMvc.perform(update(UUID.randomUUID(), "{\"status\": \"SCHEDULED\"}").with(administrator("mentor-admin@test.local")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Mentorship follow-up not found."));
    }

    @Test
    void everyRoleWithoutManageAccessIsForbiddenAndNothingChanges() throws Exception {
        MentorshipFollowUp followUp = saved("Open");

        for (String role : TrainingFixtures.NON_MANAGE_ROLES) {
            mockMvc.perform(update(followUp.getId(), "{\"status\": \"COMPLETED\"}")
                            .with(user(role.toLowerCase() + "@test.local").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(update(followUp.getId(), "{\"status\": \"COMPLETED\"}")).andExpect(status().isUnauthorized());

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, followUp.getId()).isEmpty());
        entityManager.clear();
        assertEquals(FollowUpStatus.PENDING_FOLLOW_UP, followUps.findById(followUp.getId()).orElseThrow().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"notes\": \"only notes\"}", "{\"status\": null}"})
    void aMissingStatusIsAValidationError(String json) throws Exception {
        MentorshipFollowUp followUp = saved("Open");

        mockMvc.perform(update(followUp.getId(), json).with(administrator("mentor-admin@test.local")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors").isNotEmpty());

        assertTrue(TrainingFixtures.auditLines(entityManager, jdbcTemplate, followUp.getId()).isEmpty());
    }

    @Test
    void oversizedNotesAreAValidationError() throws Exception {
        MentorshipFollowUp followUp = saved("Open");

        mockMvc.perform(update(followUp.getId(), "{\"status\": \"SCHEDULED\", \"notes\": \"" + "x".repeat(2001) + "\"}")
                        .with(administrator("mentor-admin@test.local")))
                .andExpect(status().isUnprocessableContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"status\": \"DONE\"}",
            "{\"status\": \"SCHEDULED\", \"priority\": \"HIGH\"}"})
    void anUnknownStatusOrFieldIsABadRequest(String json) throws Exception {
        MentorshipFollowUp followUp = saved("Open");

        mockMvc.perform(update(followUp.getId(), json).with(administrator("mentor-admin@test.local")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        mockMvc.perform(patch("/operations/training/mentorship/not-a-uuid")
                        .with(administrator("mentor-admin@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"SCHEDULED\"}"))
                .andExpect(status().isBadRequest());
    }

    private MentorshipFollowUp saved(String notes) {
        return followUps.saveAndFlush(new MentorshipFollowUp(
                UUID.randomUUID(), "Prototype check-in", "Amina Nakato", "Dr Okello", LocalDate.of(2026, 12, 5),
                "Prototype review", null, notes, TrainingFixtures.SEEDED_AT));
    }

    private static MockHttpServletRequestBuilder update(UUID id, String json) {
        return patch("/operations/training/mentorship/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private static RequestPostProcessor administrator(String email) {
        return user(email).roles("ADMINISTRATOR");
    }
}
