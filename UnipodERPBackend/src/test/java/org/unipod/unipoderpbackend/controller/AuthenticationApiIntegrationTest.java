package org.unipod.unipoderpbackend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationApiIntegrationTest {
    private static final String ADMIN_EMAIL = "auth-admin@example.test";
    private static final String ADMIN_PASSWORD = "admin-password-for-tests";
    private static final String REVIEWER_EMAIL = "auth-reviewer@example.test";
    private static final String REVIEWER_PASSWORD = "reviewer-password-for-tests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanLoginAndProvisionUsersButReadOnlyRolesCannot() throws Exception {
        authentication.bootstrapAdministrator(ADMIN_EMAIL, ADMIN_PASSWORD);
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new-reviewer@example.test",
                                  "password": "new-reviewer-password",
                                  "role": "REVIEWER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new-reviewer@example.test"))
                .andExpect(jsonPath("$.role").value("REVIEWER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        authentication.createUser(new CreateApiUserRequest(
                REVIEWER_EMAIL,
                REVIEWER_PASSWORD,
                ApiUserRole.REVIEWER
        ));
        String reviewerToken = login(REVIEWER_EMAIL, REVIEWER_PASSWORD);

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unauthorized-user@example.test",
                                  "password": "another-password-for-tests",
                                  "role": "OBSERVER"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/submissions"))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("email", email, "password", password)
                        )))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
