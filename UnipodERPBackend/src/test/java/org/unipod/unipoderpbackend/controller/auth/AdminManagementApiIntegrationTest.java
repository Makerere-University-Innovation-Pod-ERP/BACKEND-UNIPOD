package org.unipod.unipoderpbackend.controller.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationResource;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import org.unipod.unipoderpbackend.service.authorization.AuthorizationService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminManagementApiIntegrationTest {
    private static final String PASSWORD = "management-test-password-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private AuthorizationService authorization;

    private String adminToken;
    private String adminEmail;

    @BeforeEach
    void setUp() throws Exception {
        adminEmail = "management-admin-" + UUID.randomUUID() + "@example.test";
        authentication.createUser(new CreateApiUserRequest(
                adminEmail,
                PASSWORD,
                ApiUserRole.ADMINISTRATOR,
                "Management Admin",
                null
        ));
        adminToken = login(adminEmail);
    }

    @Test
    void managementCatalogRoutesReturnFrontendShapes() throws Exception {
        mockMvc.perform(get("/admin/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == '" + adminEmail + "')].fullName").value(
                        org.hamcrest.Matchers.contains("Management Admin")
                ))
                .andExpect(jsonPath("$[?(@.email == '" + adminEmail + "')].version").isNotEmpty());

        mockMvc.perform(get("/admin/departments").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/roles").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'REVIEWER')].permissions").isNotEmpty());

        mockMvc.perform(get("/admin/permissions").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'CASE_REVIEW')]").isNotEmpty());
    }

    @Test
    void departmentAndStaffLifecycleUseVersionedAdminRoutes() throws Exception {
        String departmentBody = """
                {"name":"Management Test Unit","notificationEmail":"unit@example.test","active":true}
                """;
        String department = mockMvc.perform(post("/admin/departments")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(departmentBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationEmail").value("unit@example.test"))
                .andReturn().getResponse().getContentAsString();
        String departmentId = objectMapper.readTree(department).get("id").asString();
        long departmentVersion = objectMapper.readTree(department).get("version").asLong();

        String staff = mockMvc.perform(post("/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Managed Staff",
                                  "email":"managed-staff@example.test",
                                  "password":"managed-staff-password-1",
                                  "role":"REVIEWER",
                                  "departmentId":"%s"
                                }
                                """.formatted(departmentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fullName").value("Managed Staff"))
                .andExpect(jsonPath("$.departmentId").value(departmentId))
                .andReturn().getResponse().getContentAsString();
        String staffId = objectMapper.readTree(staff).get("id").asString();
        long staffVersion = objectMapper.readTree(staff).get("version").asLong();

        mockMvc.perform(patch("/admin/users/" + staffId + "/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false,\"version\":" + staffVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(put("/admin/departments/" + departmentId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Management Test Unit",
                                  "notificationEmail":"unit@example.test",
                                  "active":false,
                                  "version":%d
                                }
                                """.formatted(departmentVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void savedRolePermissionsAreUsedByAuthorization() throws Exception {
        mockMvc.perform(put("/admin/roles/REVIEWER/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[],\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(jsonPath("$.version").value(1));

        Actor reviewer = new Actor("reviewer@example.test", ApiUserRole.REVIEWER, java.util.Set.of());
        var decision = authorization.check(
                reviewer,
                AuthorizationAction.CASE_REVIEW,
                new AuthorizationResource("submission", UUID.randomUUID(), null, java.util.Set.of("reviewer@example.test"), null)
        );
        org.junit.jupiter.api.Assertions.assertFalse(decision.allowed());
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
