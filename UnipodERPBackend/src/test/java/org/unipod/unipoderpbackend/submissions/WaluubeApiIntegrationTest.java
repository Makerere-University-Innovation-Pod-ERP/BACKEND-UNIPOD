package org.unipod.unipoderpbackend.submissions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WaluubeApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SectorService sectors;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void intakeWorkflowIsAvailableThroughHttpEndpoints() throws Exception {
        String actor = "workflow-" + UUID.randomUUID();
        String submitter = "submitter-" + actor;
        var receptionist = user(actor).roles("RECEPTIONIST");

        String created = mockMvc.perform(post("/submissions")
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "HTTP workflow test",
                                  "purpose": "Verify the workflow API",
                                  "problemAddressed": "The workflow API needs end-to-end coverage",
                                  "submitterId": "%s"
                                }
                                """.formatted(submitter)))
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

        mockMvc.perform(get("/submissions/mine")
                        .with(user(actor).roles("RECEPTIONIST"))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty());

        mockMvc.perform(get("/me/submissions")
                        .with(user(submitter).roles("SUBMITTER"))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty());

        mockMvc.perform(get("/me/capabilities")
                        .with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRegisterSubmission").value(false))
                .andExpect(jsonPath("$.lockedModules").isArray())
                .andExpect(jsonPath("$.registrationMessage").value(
                        "Only authorized intake roles can register a submission."
                ));

        mockMvc.perform(post("/submissions")
                        .with(user(submitter).roles("SUBMITTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Blocked submitter write",
                                  "purpose": "Must be rejected",
                                  "problemAddressed": "Submitters are read-only"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "You do not have permission to perform this action."
                ));

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

        String sectorId = sectors.create(new SectorRequest(
                "Test sector " + UUID.randomUUID(),
                "sector@example.invalid",
                true
        )).id().toString();
        mockMvc.perform(post("/submissions/{id}/forward", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Sector not found"));

        mockMvc.perform(post("/submissions/{id}/forward", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":\"" + sectorId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("FORWARDED"));
        assertEquals(
                "sector@example.invalid",
                jdbcTemplate.queryForObject(
                        "SELECT payload ->> 'notificationEmail' FROM notification_outbox WHERE aggregate_id = ?",
                        String.class,
                        UUID.fromString(id)
                )
        );

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
    void sectorAdministrationIsRestrictedAndSupportsLifecycle() throws Exception {
        String name = "Sector API " + UUID.randomUUID();
        String body = """
                {
                  "name": "%s",
                  "notificationEmail": "sector@example.invalid",
                  "active": true,
                  "version": 0
                }
                """.formatted(name);

        mockMvc.perform(post("/sectors")
                        .with(user("receptionist").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/sectors")
                        .with(user("admin").roles("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(created);
        assertTrue(idMatcher.find(), "Sector response should include the sector id.");
        String id = idMatcher.group(1);

        mockMvc.perform(put("/sectors/{id}", id)
                        .with(user("receptionist").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/sectors/{id}", UUID.randomUUID())
                        .with(user("admin").roles("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/sectors/{id}", id)
                        .with(user("admin").roles("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"active\": true", "\"active\": false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/sectors/catalog").with(user("admin").roles("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].active").value(false));
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

    @Test
    void submissionListAcceptsLowercaseCategoryAndSearchRejectsBlankCode() throws Exception {
        mockMvc.perform(get("/submissions")
                        .with(user("observer").roles("OBSERVER"))
                        .param("category", "student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(get("/submissions/search")
                        .with(user("observer").roles("OBSERVER"))
                        .param("code", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }
}
