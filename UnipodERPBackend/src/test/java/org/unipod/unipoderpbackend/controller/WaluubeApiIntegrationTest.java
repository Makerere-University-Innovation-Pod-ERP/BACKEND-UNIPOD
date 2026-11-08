package org.unipod.unipoderpbackend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WaluubeApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void intakeWorkflowIsAvailableThroughHttpEndpoints() throws Exception {
        String actor = "workflow-" + UUID.randomUUID();
        var receptionist = user(actor).roles("RECEPTIONIST");

        String created = mockMvc.perform(post("/submissions")
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "HTTP workflow test",
                                  "purpose": "Verify the workflow API",
                                  "problemAddressed": "The workflow API needs end-to-end coverage"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(created);
        assertTrue(idMatcher.find(), "Create response should include the submission id.");
        String id = idMatcher.group(1);

        mockMvc.perform(get("/submissions/mine")
                        .with(receptionist)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty());

        mockMvc.perform(get("/submissions")
                        .with(receptionist)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(post("/submissions/{id}/register", id).with(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("RECEIVED"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        mockMvc.perform(post("/submissions/{id}/documents", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Project brief",
                                  "status": "SUBMITTED",
                                  "fileReference": "brief_012345"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileReference").value("brief_012345"));

        mockMvc.perform(post("/submissions/{id}/forward", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("FORWARDED"));

        mockMvc.perform(post("/submissions/{id}/transition", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("IN_PROGRESS"));

        mockMvc.perform(post("/submissions/{id}/transition", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("COMPLETED"));

        mockMvc.perform(get("/submissions/{id}", id).with(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.id").value(id))
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.stageHistory.length()").value(5));

        mockMvc.perform(get("/queues/reception")
                        .with(receptionist)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(get("/dashboard/overview").with(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubmissions").isNumber());
    }

    @Test
    void intakeRoleCanReadQueueAndDashboard() throws Exception {
        mockMvc.perform(get("/queues/reception").with(user("receptionist").roles("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.inQueueCount").isNumber())
                .andExpect(jsonPath("$.awaitingActionCount").isNumber())
                .andExpect(jsonPath("$.recentlyUpdatedCount").isNumber());

        mockMvc.perform(get("/dashboard/overview").with(user("coordinator").roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStage.DRAFT").isNumber())
                .andExpect(jsonPath("$.completedCount").isNumber());
    }

    @Test
    void nonIntakeRoleCannotReadSubmissions() throws Exception {
        mockMvc.perform(get("/submissions").with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
    }

    @Test
    void documentEndpointRejectsUrlInsteadOfOpaqueFileReference() throws Exception {
        String request = """
                {
                  "name": "Project brief",
                  "status": "SUBMITTED",
                  "fileReference": "https://example.invalid/private-file"
                }
                """;

        mockMvc.perform(post("/submissions/{id}/documents", UUID.randomUUID())
                        .with(user("receptionist").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("One or more request values are invalid."));
    }
}
