package org.unipod.unipoderpbackend.controller.finance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.unipod.unipoderpbackend.dto.auth.CreateApiUserRequest;
import org.unipod.unipoderpbackend.dto.sector.SectorRequest;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.repository.sector.SectorMemberRepository;
import org.unipod.unipoderpbackend.service.auth.AuthenticationService;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import tools.jackson.databind.ObjectMapper;

final class FinanceTestSupport {
    static final String PASSWORD = "finance-test-password-1";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final AuthenticationService authentication;
    private final SectorService sectors;
    private final SectorMemberRepository members;

    FinanceTestSupport(MockMvc mockMvc, ObjectMapper objectMapper, AuthenticationService authentication,
                       SectorService sectors, SectorMemberRepository members) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.authentication = authentication;
        this.sectors = sectors;
        this.members = members;
    }

    UUID department(String name) {
        return sectors.create(new SectorRequest(name, "dept-" + UUID.randomUUID() + "@example.com", true)).id();
    }

    String user(String email, ApiUserRole role, UUID... departmentIds) throws Exception {
        authentication.createUser(new CreateApiUserRequest(email, PASSWORD, role));
        for (UUID departmentId : departmentIds) {
            members.insertIfAbsent(UUID.randomUUID(), departmentId, email);
        }
        return login(email);
    }

    String login(String email) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(response).get("accessToken").asString();
    }

    String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    String read(String body, String field) throws Exception {
        return objectMapper.readTree(body).get(field).asString();
    }
}
