package org.unipod.unipoderpbackend.controller.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.auth.ApiUserResponse;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAdministrationApiIntegrationTest {
    private static final String PASSWORD = "user-admin-password-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private ApiUserRepository users;

    private String adminToken;
    private ApiUserResponse reviewer;

    @BeforeEach
    void setUp() throws Exception {
        users.findAll().stream()
                .filter(user -> user.getRole() == ApiUserRole.ADMINISTRATOR)
                .forEach(users::delete);
        users.flush();
        authentication.createUser(new CreateApiUserRequest("ua-admin@example.test", PASSWORD, ApiUserRole.ADMINISTRATOR));
        reviewer = authentication.createUser(new CreateApiUserRequest("ua-reviewer@example.test", PASSWORD, ApiUserRole.REVIEWER));
        adminToken = login("ua-admin@example.test");
    }

    @Test
    void adminListsAndFiltersUsers() throws Exception {
        mockMvc.perform(get("/users").param("role", "REVIEWER").param("q", "ua-rev").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].email").value("ua-reviewer@example.test"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
    }

    @Test
    void nonAdminsAreRefusedWithACode() throws Exception {
        String reviewerToken = login("ua-reviewer@example.test");
        mockMvc.perform(get("/users").header("Authorization", bearer(reviewerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_PERMITTED"));
    }

    @Test
    void adminChangesARoleToTheNewFinanceRole() throws Exception {
        mockMvc.perform(patch("/users/" + reviewer.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"FINANCE_OFFICER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FINANCE_OFFICER"));

        mockMvc.perform(get("/activity").param("action", "user.role_changed").param("entityId", reviewer.id().toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].before.role").value("REVIEWER"))
                .andExpect(jsonPath("$.items[0].after.role").value("FINANCE_OFFICER"));
    }

    @Test
    void deactivatedUsersCannotLogInUntilReactivated() throws Exception {
        mockMvc.perform(post("/users/" + reviewer.id() + "/deactivate").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(loginRequest("ua-reviewer@example.test")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/users/" + reviewer.id() + "/reactivate").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(loginRequest("ua-reviewer@example.test")).andExpect(status().isOk());
    }

    @Test
    void theLastAdministratorCannotBeLockedOut() throws Exception {
        String adminId = users.findByEmailIgnoreCase("ua-admin@example.test").orElseThrow().getId().toString();
        mockMvc.perform(post("/users/" + adminId + "/deactivate").header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/users/" + adminId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"OBSERVER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCreatesADepartmentAndPutsAUserInIt() throws Exception {
        String created = mockMvc.perform(post("/departments")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"UA Textile\", \"notificationEmail\": \"textile@example.com\", \"active\": true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String departmentId = objectMapper.readTree(created).get("id").asString();

        mockMvc.perform(get("/departments").header("Authorization", bearer(login("ua-reviewer@example.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'UA Textile')].id").value(departmentId));

        mockMvc.perform(patch("/users/" + reviewer.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentIds\": [\"" + departmentId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentIds[0]").value(departmentId));

        mockMvc.perform(patch("/users/" + reviewer.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentIds\": []}"))
                .andExpect(jsonPath("$.departmentIds").isEmpty());
    }

    @Test
    void rolesListIncludesTheNewRoles() throws Exception {
        mockMvc.perform(get("/roles").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.role == 'FINANCE_OFFICER')].staff").value(true))
                .andExpect(jsonPath("$[?(@.role == 'DEPARTMENT_COORDINATOR')].staff").value(true))
                .andExpect(jsonPath("$[?(@.role == 'SUBMITTER')].staff").value(false));
    }

    @Test
    void unknownUserIs404() throws Exception {
        mockMvc.perform(get("/users/00000000-0000-0000-0000-000000000000").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private org.springframework.test.web.servlet.RequestBuilder loginRequest(String email) throws Exception {
        return post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD)));
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(loginRequest(email))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asString();
    }
}
