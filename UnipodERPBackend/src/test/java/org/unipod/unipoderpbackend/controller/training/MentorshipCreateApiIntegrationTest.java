package org.unipod.unipoderpbackend.controller.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MentorshipCreateApiIntegrationTest {
    private static final String BODY = """
            {"title": "Prototype check-in", "mentee": "Amina Nakato", "mentor": "Dr Okello",
             "scheduledDate": "2026-12-05", "focus": "Prototype review", "priority": "HIGH",
             "notes": "Bring the demo"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void createsAFollowUpAndAuditsItWithoutPersonalDetails() throws Exception {
        String response = mockMvc.perform(post("/operations/training/mentorship")
                        .with(administrator("mentor-admin@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Prototype check-in"))
                .andExpect(jsonPath("$.mentee").value("Amina Nakato"))
                .andExpect(jsonPath("$.mentor").value("Dr Okello"))
                .andExpect(jsonPath("$.scheduledDate").value("2026-12-05"))
                .andExpect(jsonPath("$.focus").value("Prototype review"))
                .andExpect(jsonPath("$.status").value("PENDING_FOLLOW_UP"))
                .andExpect(jsonPath("$.notes").value("Bring the demo"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asString());

        List<Map<String, Object>> lines = TrainingFixtures.auditLines(entityManager, jdbcTemplate, id);
        assertEquals(1, lines.size());
        Map<String, Object> line = lines.get(0);
        assertEquals("mentor-admin@test.local", line.get("actor"));
        assertEquals("ADMINISTRATOR", line.get("actor_role"));
        assertEquals("training.follow_up.created", line.get("action"));
        assertEquals("mentorship_follow_up", line.get("entity_type"));
        assertEquals(null, line.get("department_id"));
        assertEquals(null, line.get("before_value"));
        assertTrue(line.get("after_value").toString().contains("PENDING_FOLLOW_UP"));
        assertTrue(line.get("after_value").toString().contains("2026-12-05"));
        for (String personal : List.of("Amina", "Nakato", "Okello", "Prototype", "Bring the demo")) {
            assertFalse(line.get("after_value").toString().contains(personal), "audit line leaks: " + personal);
        }
    }

    @Test
    void appliesDefaultsAndAppearsInTheOverview() throws Exception {
        String response = mockMvc.perform(post("/operations/training/mentorship")
                        .with(administrator("mentor-admin@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Check-in", "mentee": "Amina", "mentor": "Dr Okello",
                                 "scheduledDate": "2026-12-05", "focus": "Prototype"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.notes").value(""))
                .andExpect(jsonPath("$.status").value("PENDING_FOLLOW_UP"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).get("id").asString();

        mockMvc.perform(get("/operations/training/overview").with(administrator("mentor-admin@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.followUps[?(@.id == '" + id + "')].mentee").value("Amina"));
    }

    @Test
    void everyRoleWithoutManageAccessIsForbiddenAndWritesNothing() throws Exception {
        Integer before = jdbcTemplate.queryForObject("SELECT count(*) FROM mentorship_follow_ups", Integer.class);

        for (String role : TrainingFixtures.NON_MANAGE_ROLES) {
            mockMvc.perform(post("/operations/training/mentorship")
                            .with(user(role.toLowerCase() + "@test.local").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/operations/training/mentorship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        assertEquals(before, jdbcTemplate.queryForObject("SELECT count(*) FROM mentorship_follow_ups", Integer.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void rejectsAnInvalidBodyWithValidationErrorsAndWritesNothing(String problem, String json) throws Exception {
        Integer before = jdbcTemplate.queryForObject("SELECT count(*) FROM mentorship_follow_ups", Integer.class);

        mockMvc.perform(post("/operations/training/mentorship")
                        .with(administrator("mentor-admin@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors").isNotEmpty());

        assertEquals(before, jdbcTemplate.queryForObject("SELECT count(*) FROM mentorship_follow_ups", Integer.class));
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("blank title", BODY.replace("Prototype check-in", " ")),
                Arguments.of("title over 200", BODY.replace("Prototype check-in", "x".repeat(201))),
                Arguments.of("missing mentee", BODY.replace("\"mentee\": \"Amina Nakato\",", "")),
                Arguments.of("mentee over 200", BODY.replace("Amina Nakato", "x".repeat(201))),
                Arguments.of("blank mentor", BODY.replace("Dr Okello", "")),
                Arguments.of("mentor over 200", BODY.replace("Dr Okello", "x".repeat(201))),
                Arguments.of("missing date", BODY.replace("\"scheduledDate\": \"2026-12-05\",", "")),
                Arguments.of("blank focus", BODY.replace("Prototype review", "")),
                Arguments.of("focus over 500", BODY.replace("Prototype review", "x".repeat(501))),
                Arguments.of("notes over 2000", BODY.replace("Bring the demo", "x".repeat(2001))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedBodies")
    void rejectsAMalformedBodyWithBadRequest(String problem, String json) throws Exception {
        mockMvc.perform(post("/operations/training/mentorship")
                        .with(administrator("mentor-admin@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    static Stream<Arguments> malformedBodies() {
        return Stream.of(
                Arguments.of("unknown priority", BODY.replace("HIGH", "CRITICAL")),
                Arguments.of("date not ISO", BODY.replace("2026-12-05", "5 Dec 2026")),
                Arguments.of("status is not accepted on create", BODY.replace("\"priority\"", "\"status\": \"COMPLETED\", \"priority\"")));
    }

    private static RequestPostProcessor administrator(String email) {
        return user(email).roles("ADMINISTRATOR");
    }
}
