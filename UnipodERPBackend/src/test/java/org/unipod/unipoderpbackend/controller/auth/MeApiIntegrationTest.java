package org.unipod.unipoderpbackend.controller.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MeApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationService authentication;

    @Test
    void submitterSeesTheirProfileAndOwnScope() throws Exception {
        String response = mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.ofEntries(
                                Map.entry("accountKind", "STUDENT"),
                                Map.entry("fullName", "Dr. Miriam Nsubuga"),
                                Map.entry("displayFirstName", "Miriam"),
                                Map.entry("phone", "0700000010"),
                                Map.entry("email", "miriam@example.test"),
                                Map.entry("password", "miriam-password-1"),
                                Map.entry("studentNumber", "2400711111"),
                                Map.entry("course", "MSc Engineering"),
                                Map.entry("universityEmail", "miriam@students.mak.ac.ug"),
                                Map.entry("rulesAccepted", true),
                                Map.entry("rulesVersion", "2026-1")
                        ))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asString();

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("miriam@example.test"))
                .andExpect(jsonPath("$.role").value("SUBMITTER"))
                .andExpect(jsonPath("$.homeScreen").value("MY_SUBMISSIONS"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.profile.accountKind").value("STUDENT"))
                .andExpect(jsonPath("$.profile.displayFirstName").value("Miriam"))
                .andExpect(jsonPath("$.profile.studentNumber").value("2400711111"))
                .andExpect(jsonPath("$.capabilities[?(@.action == 'CASE_VIEW')].scope").value("OWN"))
                .andExpect(jsonPath("$.capabilities[?(@.action == 'AUDIT_VIEW')]").isEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void staffWithoutAProfileLandOnTheStaffHome() throws Exception {
        authentication.createUser(new CreateApiUserRequest(
                "me-coordinator@example.test", "coordinator-password-1", ApiUserRole.INNOVATION_COORDINATOR));
        String token = login("me-coordinator@example.test", "coordinator-password-1");

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INNOVATION_COORDINATOR"))
                .andExpect(jsonPath("$.homeScreen").value("STAFF_HOME"))
                .andExpect(jsonPath("$.profile").isEmpty())
                .andExpect(jsonPath("$.capabilities[?(@.action == 'AUDIT_VIEW')].scope").value("ALL"));
    }

    @Test
    void meNeedsAToken() throws Exception {
        mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asString();
    }
}
