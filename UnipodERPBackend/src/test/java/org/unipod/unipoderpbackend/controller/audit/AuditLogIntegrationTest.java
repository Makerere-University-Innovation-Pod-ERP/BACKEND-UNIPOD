package org.unipod.unipoderpbackend.controller.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLogIntegrationTest {
    private static final String PASSWORD = "audit-password-for-tests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditService auditService;

    @Test
    void creatingAUserIsAuditedAndShowsUpInActivity() throws Exception {
        String admin = createUser("audit-admin@example.test", ApiUserRole.ADMINISTRATOR);
        String token = login(admin);

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "audit-test-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "new-reviewer@example.test",
                                "password", "a-long-enough-password",
                                "role", "REVIEWER"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/activity")
                        .param("action", "user.created")
                        .param("actor", "AUDIT-ADMIN@example.test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].actor").value("audit-admin@example.test"))
                .andExpect(jsonPath("$.items[0].actorRole").value("ADMINISTRATOR"))
                .andExpect(jsonPath("$.items[0].entityType").value("user"))
                .andExpect(jsonPath("$.items[0].after.email").value("new-reviewer@example.test"))
                .andExpect(jsonPath("$.items[0].after.role").value("REVIEWER"))
                .andExpect(jsonPath("$.items[0].before").doesNotExist())
                .andExpect(jsonPath("$.items[0].requestId").value("audit-test-1"))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void roleWithoutAuditViewGetsForbiddenWithCode() throws Exception {
        String submitter = createUser("audit-submitter@example.test", ApiUserRole.SUBMITTER);

        mockMvc.perform(get("/activity").header("Authorization", "Bearer " + login(submitter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_PERMITTED"))
                .andExpect(jsonPath("$.detail").value("Your role is not allowed to do this."));
    }

    @Test
    void activityNeedsALogin() throws Exception {
        mockMvc.perform(get("/activity")).andExpect(status().isUnauthorized());
    }

    @Test
    void historyOfAnUnknownCaseIsNotFound() throws Exception {
        String admin = createUser("audit-admin2@example.test", ApiUserRole.ADMINISTRATOR);

        mockMvc.perform(get("/cases/{id}/activity", UUID.randomUUID())
                        .header("Authorization", "Bearer " + login(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void databaseRefusesToUpdateAnAuditLine() {
        UUID id = insertAuditLine();

        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("UPDATE audit_log SET actor = 'someone-else' WHERE id = ?", id));
    }

    @Test
    void databaseRefusesToDeleteAnAuditLine() {
        UUID id = insertAuditLine();

        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("DELETE FROM audit_log WHERE id = ?", id));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void logRefusesToRunOutsideATransaction() {
        Actor admin = new Actor("admin@example.test", ApiUserRole.ADMINISTRATOR, Set.of());

        assertThrows(IllegalTransactionStateException.class,
                () -> auditService.log(AuditEvent.created(admin, "user.created", "user", null, null)));
    }

    @Test
    void submissionLifecycleIsAuditedAndShowsUpInCaseHistory() throws Exception {
        String submitter = createUser("audit-submitter2@example.test", ApiUserRole.SUBMITTER);
        String submitterToken = login(submitter);
        String admin = createUser("audit-admin3@example.test", ApiUserRole.ADMINISTRATOR);
        String adminToken = login(admin);

        String created = mockMvc.perform(post("/submissions")
                        .header("Authorization", "Bearer " + submitterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "GENERAL",
                                "title", "Audited brief",
                                "purpose", "Purpose",
                                "problemAddressed", "Problem",
                                "acceptedRulesVersion", "2026-01"
                        ))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String caseId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/submissions/{id}/submit", caseId)
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/cases/{id}/activity", caseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[*].action",
                        hasItems("submission.created", "submission.submitted")))
                .andExpect(jsonPath("$.items[*].actor", hasItems(submitter)))
                .andExpect(jsonPath("$.items[*].actorRole", hasItems("SUBMITTER")))
                .andExpect(jsonPath("$.items[*].entityId", hasItems(caseId)));

        mockMvc.perform(get("/activity")
                        .param("entityType", "submission")
                        .param("entityId", caseId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void assistedSubmissionSucceedsWithAConfiguredPublicBaseUrl() throws Exception {
        String admin = createUser("audit-admin4@example.test", ApiUserRole.ADMINISTRATOR);
        String token = login(admin);

        mockMvc.perform(post("/submissions/assisted")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "submitterEmail", "walkin-audit@example.test",
                                "category", "GENERAL",
                                "title", "Walk-in brief",
                                "purpose", "Purpose",
                                "problemAddressed", "Problem"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.account.created").value(true))
                .andExpect(jsonPath("$.account.setPasswordToken").isNotEmpty())
                .andExpect(jsonPath("$.account.setPasswordUrl",
                        containsString("/set-password?token=")))
                .andExpect(jsonPath("$.account.setPasswordUrl", startsWith("http")));
    }

    private UUID insertAuditLine() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO audit_log (id, occurred_at, actor, actor_role, action, entity_type)
                VALUES (?, now(), 'admin@example.test', 'ADMINISTRATOR', 'user.created', 'user')
                """, id);
        return id;
    }

    private String createUser(String email, ApiUserRole role) {
        users.saveAndFlush(new ApiUser(UUID.randomUUID(), email, passwordEncoder.encode(PASSWORD), role, Instant.now()));
        return email;
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
