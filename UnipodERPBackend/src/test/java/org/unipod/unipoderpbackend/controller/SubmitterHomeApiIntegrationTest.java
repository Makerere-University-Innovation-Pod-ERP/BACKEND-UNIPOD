package org.unipod.unipoderpbackend.controller;

import java.util.Map;
import java.util.UUID;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmitterHomeApiIntegrationTest {
    private static final String PASSWORD = "submitter-home-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authentication;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitterHomeWorksEndToEndWithBearerTokens() throws Exception {
        String email = "w3-" + UUID.randomUUID() + "@example.test";
        authentication.createUser(new CreateApiUserRequest(email, PASSWORD, ApiUserRole.SUBMITTER));
        String token = login(email, PASSWORD);

        String draftId = createSubmission(token, """
                {
                  "category": "general",
                  "title": "Bearer-token draft",
                  "purpose": "Verify the submitter home",
                  "problemAddressed": "The home screen needs end-to-end coverage"
                }
                """);

        mockMvc.perform(get("/me/submissions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(draftId))
                .andExpect(jsonPath("$.items[0].stage").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].pendingAction").value("CONTINUE_DRAFT"))
                .andExpect(jsonPath("$.items[0].waitingForYou").value("Continue your draft and submit it."))
                .andExpect(jsonPath("$.items[0].waitingSince").isNotEmpty())
                .andExpect(jsonPath("$.items[0].registeredBy").doesNotExist());

        String draftDetail = mockMvc.perform(get("/me/submissions/{id}", draftId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var draftJson = objectMapper.readTree(draftDetail);
        assertEquals(draftJson.get("createdAt").asText(), draftJson.get("waitingSince").asText());

        mockMvc.perform(get("/me/capabilities")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canCreateBrief").value(true))
                .andExpect(jsonPath("$.canEditDraft").value(true))
                .andExpect(jsonPath("$.canSubmitDraft").value(true))
                .andExpect(jsonPath("$.canRespondToInformationRequest").value(true))
                .andExpect(jsonPath("$.canUploadDocuments").value(true))
                .andExpect(jsonPath("$.canUploadPaymentProof").value(false))
                .andExpect(jsonPath("$.canBook").value(false))
                .andExpect(jsonPath("$.modules.length()").value(4))
                .andExpect(jsonPath("$.modules[0].key").value("BRIEFS"))
                .andExpect(jsonPath("$.modules[0].availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.modules[2].key").value("INVOICES"))
                .andExpect(jsonPath("$.modules[2].availability").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.modules[3].key").value("BOOKINGS"))
                .andExpect(jsonPath("$.modules[3].availability").value("NOT_CONFIGURED"));

        mockMvc.perform(get("/me/submissions")
                        .header("Authorization", "Bearer " + token)
                        .param("tab", "SERVICE_REQUESTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        createSubmission(token, """
                {
                  "category": "service_request",
                  "title": "Bearer-token service request",
                  "purpose": "Verify the service-request tab",
                  "problemAddressed": "The lab needs support",
                  "serviceRequested": "Prototype build",
                  "expectedResult": "A working prototype"
                }
                """);

        mockMvc.perform(get("/me/submissions")
                        .header("Authorization", "Bearer " + token)
                        .param("tab", "BRIEFS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/me/submissions")
                        .header("Authorization", "Bearer " + token)
                        .param("tab", "service_requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/me/submissions")
                        .header("Authorization", "Bearer " + token)
                        .param("tab", "invoices"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/me/submissions")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "101"))
                .andExpect(status().isUnprocessableContent());

        String otherEmail = "w3-other-" + UUID.randomUUID() + "@example.test";
        authentication.createUser(new CreateApiUserRequest(otherEmail, PASSWORD, ApiUserRole.SUBMITTER));
        String otherToken = login(otherEmail, PASSWORD);

        mockMvc.perform(get("/me/submissions")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/submissions")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/me/submissions/{id}", draftId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/cases/{id}", draftId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        String observerEmail = "w3-observer-" + UUID.randomUUID() + "@example.test";
        authentication.createUser(new CreateApiUserRequest(observerEmail, PASSWORD, ApiUserRole.OBSERVER));
        String observerToken = login(observerEmail, PASSWORD);

        mockMvc.perform(get("/me/capabilities")
                        .header("Authorization", "Bearer " + observerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/me/submissions"))
                .andExpect(status().isUnauthorized());
    }

    private String createSubmission(String token, String body) throws Exception {
        String response = mockMvc.perform(post("/submissions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", email, "password", password)
                        )))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
