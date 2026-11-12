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
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.unipod.unipoderpbackend.service.config.ConfigService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainingSessionCreateApiIntegrationTest {
    private static final String FULL_BODY = """
            {
              "title": "Welding safety",
              "facilitator": "Eng. Nsubuga",
              "date": "2027-03-10T09:00:00+03:00",
              "endsAt": "2027-03-10T12:00:00+03:00",
              "capacity": 12,
              "venue": "Workshop 1",
              "description": "PPE and fume control",
              "departmentId": "DEPT",
              "fee": 50000,
              "safetyCourse": true,
              "publish": true
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConfigService config;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID departmentId;

    @BeforeEach
    void seedDepartment() {
        departmentId = TrainingFixtures.department(jdbcTemplate);
        // The department list is cached; this test's department exists only inside its transaction.
        config.refresh();
    }

    @AfterEach
    void forgetTheRolledBackDepartment() {
        config.refresh();
    }

    @Test
    void createsASessionWithEveryFieldAndAuditsIt() throws Exception {
        String response = mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("Training.Admin@Test.Local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(FULL_BODY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Welding safety"))
                .andExpect(jsonPath("$.facilitator").value("Eng. Nsubuga"))
                .andExpect(jsonPath("$.date").value("2027-03-10T09:00:00+03:00"))
                .andExpect(jsonPath("$.endsAt").value("2027-03-10T12:00:00+03:00"))
                .andExpect(jsonPath("$.capacity").value(12))
                .andExpect(jsonPath("$.enrolledCount").value(0))
                .andExpect(jsonPath("$.venue").value("Workshop 1"))
                .andExpect(jsonPath("$.status").value("UPCOMING"))
                .andExpect(jsonPath("$.description").value("PPE and fume control"))
                .andExpect(jsonPath("$.departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$.fee").value(50_000))
                .andExpect(jsonPath("$.safetyCourse").value(true))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.participants").isEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asString());

        List<Map<String, Object>> rows = auditRows(id);
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("training.admin@test.local", row.get("actor"));
        assertEquals("ADMINISTRATOR", row.get("actor_role"));
        assertEquals("training.session.created", row.get("action"));
        assertEquals("training_session", row.get("entity_type"));
        assertEquals(departmentId, row.get("department_id"));
        assertEquals(null, row.get("before_value"));
        JsonNode after = objectMapper.readTree(row.get("after_value").toString());
        assertEquals("Welding safety", after.get("title").asString());
        assertEquals(12, after.get("capacity").asInt());
        assertEquals(50_000, after.get("fee").asInt());
        assertFalse(row.get("after_value").toString().contains("Nsubuga"), "the facilitator's name is personal data");
    }

    @Test
    void appliesDefaultsWhenOnlyTheRequiredFieldsAreSent() throws Exception {
        mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("defaults@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"title": "Soldering", "facilitator": "Eng. Nsubuga",
                                 "date": "2027-03-10T09:00:00+03:00", "capacity": 8, "venue": "Lab 1",
                                 "departmentId": "DEPT"}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endsAt").value("2027-03-10T11:00:00+03:00"))
                .andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.fee").value(0))
                .andExpect(jsonPath("$.safetyCourse").value(false))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.status").value("UPCOMING"));
    }

    @Test
    void anUnpublishedSessionIsADraft() throws Exception {
        mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("draft@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"title": "Draft course", "facilitator": "Eng. Nsubuga",
                                 "date": "2027-03-10T09:00:00+03:00", "capacity": 8, "venue": "Lab 1",
                                 "departmentId": "DEPT", "publish": false}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.published").value(false));
    }

    @Test
    void theCreatedSessionCanBeReadBack() throws Exception {
        String response = mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("readback@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(FULL_BODY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).get("id").asString();

        mockMvc.perform(get("/operations/training/sessions/{id}", id).with(administrator("readback@test.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Welding safety"));
    }

    @Test
    void everyRoleWithoutManageAccessIsForbiddenAndWritesNothing() throws Exception {
        for (String role : TrainingFixtures.NON_MANAGE_ROLES) {
            mockMvc.perform(post("/operations/training/sessions")
                            .with(user(role.toLowerCase() + "@test.local").roles(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(FULL_BODY)))
                    .andExpect(status().isForbidden());
        }
        Integer created = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM training_sessions WHERE department_id = ?", Integer.class, departmentId);
        assertEquals(0, created);
    }

    @Test
    void anAnonymousCallerIsUnauthorized() throws Exception {
        mockMvc.perform(post("/operations/training/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(FULL_BODY)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    void rejectsAnInvalidBodyWithValidationErrors(String problem, String json) throws Exception {
        mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("invalid@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(json)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }

    static Stream<Arguments> invalidBodies() {
        String base = """
                {"title": "T", "facilitator": "F", "date": "2027-03-10T09:00:00+03:00",
                 "capacity": 5, "venue": "V", "departmentId": "DEPT"%s}
                """;
        return Stream.of(
                Arguments.of("blank title", base.replace("\"title\": \"T\"", "\"title\": \"  \"").formatted("")),
                Arguments.of("missing facilitator", base.replace("\"facilitator\": \"F\",", "").formatted("")),
                Arguments.of("missing date", base.replace("\"date\": \"2027-03-10T09:00:00+03:00\",", "").formatted("")),
                Arguments.of("zero capacity", base.replace("\"capacity\": 5", "\"capacity\": 0").formatted("")),
                Arguments.of("missing capacity", base.replace("\"capacity\": 5,", "").formatted("")),
                Arguments.of("blank venue", base.replace("\"venue\": \"V\"", "\"venue\": \"\"").formatted("")),
                Arguments.of("missing department", base.replace(", \"departmentId\": \"DEPT\"", "").formatted("")),
                Arguments.of("negative fee", base.formatted(", \"fee\": -1")),
                Arguments.of("title too long", base.replace("\"title\": \"T\"",
                        "\"title\": \"" + "x".repeat(201) + "\"").formatted("")));
    }

    @Test
    void anEndThatIsNotAfterTheStartIsABadRequest() throws Exception {
        mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("window@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("""
                                {"title": "T", "facilitator": "F", "date": "2027-03-10T09:00:00+03:00",
                                 "endsAt": "2027-03-10T09:00:00+03:00", "capacity": 5, "venue": "V",
                                 "departmentId": "DEPT"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A session must end after it starts."));
    }

    @Test
    void anUnknownDepartmentIsABadRequest() throws Exception {
        mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("unknown@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_BODY.replace("DEPT", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose an active department."));
    }

    @Test
    void anInactiveDepartmentIsABadRequest() throws Exception {
        UUID inactive = UUID.randomUUID();
        Timestamp now = Timestamp.from(TrainingFixtures.SEEDED_AT);
        jdbcTemplate.update(
                "INSERT INTO sectors (id, name, notification_email, active, created_at, updated_at) "
                        + "VALUES (?, ?, 'old@test.local', FALSE, ?, ?)",
                inactive, "Closed Dept " + inactive, now, now);
        config.refresh();

        mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("inactive@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FULL_BODY.replace("DEPT", inactive.toString())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDateWithoutAnOffsetOrAnUnknownFieldIsABadRequest() throws Exception {
        mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("shape@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(FULL_BODY.replace("2027-03-10T09:00:00+03:00", "2027-03-10T09:00:00"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/operations/training/sessions")
                        .with(administrator("shape@test.local"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(FULL_BODY.replace("\"fee\": 50000,", "\"fee\": 50000, \"price\": 1,"))))
                .andExpect(status().isBadRequest());
    }

    private String body(String template) {
        return template.replace("DEPT", departmentId.toString());
    }

    private List<Map<String, Object>> auditRows(UUID entityId) {
        // The audit line is a pending JPA insert until flushed; the query below goes around Hibernate.
        entityManager.flush();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT actor, actor_role, action, entity_type, department_id, before_value::text AS before_value, "
                        + "after_value::text AS after_value FROM audit_log WHERE entity_id = ?", entityId);
        assertTrue(rows.stream().allMatch(row -> row.get("action") != null));
        return rows;
    }

    private static RequestPostProcessor administrator(String email) {
        return user(email).roles("ADMINISTRATOR");
    }
}
