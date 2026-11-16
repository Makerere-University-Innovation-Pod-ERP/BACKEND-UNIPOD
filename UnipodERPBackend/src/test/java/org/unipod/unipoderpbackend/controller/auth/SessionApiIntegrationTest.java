package org.unipod.unipoderpbackend.controller.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.auth.ApiUserResponse;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionApiIntegrationTest {
    private static final String EMAIL = "session-user@example.test";
    private static final String PASSWORD = "session-password-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private ApiUserRepository users;

    private ApiUserResponse user;

    @BeforeEach
    void setUp() {
        user = authentication.createUser(new CreateApiUserRequest(EMAIL, PASSWORD, ApiUserRole.REVIEWER));
    }

    @Test
    void loginGivesARefreshTokenThatRotates() throws Exception {
        JsonNode first = login();
        String firstRefresh = first.get("refreshToken").asString();

        JsonNode second = body(refresh(firstRefresh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty()));
        String secondRefresh = second.get("refreshToken").asString();

        refresh(firstRefresh).andExpect(status().isUnauthorized());
        refresh(secondRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        String token = login().get("refreshToken").asString();
        mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", token))))
                .andExpect(status().isNoContent());
        refresh(token).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "not-a-real-token"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void changingThePasswordEndsEverySession() throws Exception {
        JsonNode session = login();
        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + session.get("accessToken").asString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "a-different-password-1"))))
                .andExpect(status().isNoContent());
        refresh(session.get("refreshToken").asString()).andExpect(status().isUnauthorized());
    }

    @Test
    void aDeactivatedAccountCannotRefresh() throws Exception {
        String token = login().get("refreshToken").asString();
        ApiUser account = users.findById(user.id()).orElseThrow();
        account.deactivate(Instant.now());
        users.saveAndFlush(account);
        refresh(token).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownRefreshTokenIs401() throws Exception {
        refresh("made-up-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private JsonNode login() throws Exception {
        return body(mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshExpiresAt").isNotEmpty()));
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("refreshToken", token))));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
