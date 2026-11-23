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
import org.unipod.unipoderpbackend.repository.operations.StaffNameQuery;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import org.unipod.unipoderpbackend.service.config.ConfigService;
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

    @Autowired
    private ConfigService config;

    @Autowired
    private StaffNameQuery staffNames;

    private String adminToken;
    private String adminEmail;

    @BeforeEach
    void setUp() throws Exception {
        adminEmail = "management-admin-" + UUID.randomUUID() + "@example.test";
        authentication.createUser(new CreateApiUserRequest(
                adminEmail,
                PASSWORD,
                ApiUserRole.ADMINISTRATOR,
                "Management Admin"
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
    void managementCreateRejectsControlCharactersInFullName() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "fullName", "Managed" + String.valueOf((char) 0) + "Staff",
                "email", "invalid-name@example.test",
                "password", PASSWORD,
                "role", "REVIEWER"
        ));
        mockMvc.perform(post("/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void departmentAndStaffLifecycleUseVersionedAdminRoutes() throws Exception {
        config.departments();
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
        org.junit.jupiter.api.Assertions.assertTrue(config.departments().stream()
                .anyMatch(item -> item.id().toString().equals(departmentId)));
        long departmentVersion = objectMapper.readTree(department).get("version").asLong();

        String secondDepartment = mockMvc.perform(post("/admin/departments")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Management Test Unit 2","notificationEmail":"unit2@example.test","active":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String secondDepartmentId = objectMapper.readTree(secondDepartment).get("id").asString();

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
                .andExpect(jsonPath("$.departmentIds[0]").value(departmentId))
                .andReturn().getResponse().getContentAsString();
        String staffId = objectMapper.readTree(staff).get("id").asString();
        long staffVersion = objectMapper.readTree(staff).get("version").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(
                "Managed Staff",
                staffNames.displayName("managed-staff@example.test")
        );
        mockMvc.perform(get("/activity")
                        .param("action", "user.departments_changed")
                        .param("entityId", staffId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/admin/users/" + staffId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"REVIEWER\",\"version\":-1}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/admin/users/" + staffId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMINISTRATOR\",\"version\":" + staffVersion + "}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/users/" + staffId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentIds\":[\"" + departmentId + "\",\"" + secondDepartmentId
                                + "\"],\"version\":" + staffVersion + "}"))
                .andExpect(status().isOk());
        staffVersion++;

        mockMvc.perform(patch("/admin/users/" + staffId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"REVIEWER\",\"version\":" + staffVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentIds.length()").value(2));

        mockMvc.perform(patch("/admin/users/" + staffId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"REVIEWER\",\"departmentId\":\"" + departmentId
                                + "\",\"version\":" + staffVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentIds.length()").value(2));

        mockMvc.perform(patch("/admin/users/" + staffId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"REVIEWER\",\"departmentId\":null,\"version\":" + staffVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentIds").isEmpty());
        staffVersion++;

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
        org.junit.jupiter.api.Assertions.assertFalse(config.departments().stream()
                                .anyMatch(item -> item.id().toString().equals(departmentId)));
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

        mockMvc.perform(put("/admin/roles/REVIEWER/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[],\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/admin/roles/REVIEWER/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[\"CASE_VIEW\",null],\"version\":1}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/admin/roles/REVIEWER/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissions\":[],\"version\":-1}"))
                .andExpect(status().isBadRequest());

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
