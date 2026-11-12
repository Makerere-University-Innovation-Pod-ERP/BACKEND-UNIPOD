package org.unipod.unipoderpbackend.exception;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProblemFormatIntegrationTest {
    private static final String PASSWORD = "problem-password-for-tests";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void invalidBodyIs422WithFieldErrors() throws Exception {
        String token = login(createUser("problem-admin@example.test", ApiUserRole.ADMINISTRATOR));

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"long-enough-password\",\"role\":\"REVIEWER\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("One or more request values are invalid."))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")));
    }

    @Test
    void invalidQueryParameterIs422WithFieldErrors() throws Exception {
        String token = login(createUser("problem-admin2@example.test", ApiUserRole.ADMINISTRATOR));

        mockMvc.perform(get("/notifications").param("size", "500").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void brokenJsonStays400WithItsOwnCode() throws Exception {
        String token = login(createUser("problem-admin3@example.test", ApiUserRole.ADMINISTRATOR));

        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    void everyRefusalCarriesACode() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String submitter = login(createUser("problem-submitter@example.test", ApiUserRole.SUBMITTER));
        mockMvc.perform(post("/admin/users")
                        .header("Authorization", "Bearer " + submitter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@example.test\",\"password\":\"long-enough-password\",\"role\":\"REVIEWER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/notifications/{id}/read", UUID.randomUUID()).header("Authorization", "Bearer " + submitter))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        mockMvc.perform(get("/no-such-endpoint").header("Authorization", "Bearer " + submitter))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
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
