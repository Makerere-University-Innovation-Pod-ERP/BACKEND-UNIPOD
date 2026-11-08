package org.unipod.unipoderpbackend.controller;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.sector.SectorRequest;
import org.unipod.unipoderpbackend.service.sector.SectorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].registeredBy").isEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].assignedReviewerSubject").isEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')].responsibleSectorId").isEmpty());

        mockMvc.perform(get("/me/capabilities")
                        .with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRegisterSubmission").value(false))
                .andExpect(jsonPath("$.lockedModules").isArray())
                .andExpect(jsonPath("$.registrationMessage").value(
                        "Only authorized intake roles can register a submission."
                ));

        mockMvc.perform(get("/me/capabilities")
                        .with(user("intake-submitters").roles("SUBMITTER", "RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canRegisterSubmission").value(true));

        mockMvc.perform(get("/submissions/mine")
                        .with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/me/submissions")
                        .with(user("other-submitter").roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isEmpty());

        mockMvc.perform(get("/me/capabilities")
                        .with(user("observer").roles("OBSERVER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/submissions/{id}", id)
                        .with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/submissions/{id}/register", id)
                        .with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isForbidden());

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

        String sectorName = "Test sector " + UUID.randomUUID();
        String sectorId = sectors.create(new SectorRequest(
                sectorName,
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
                .andExpect(jsonPath("$.stage").value("FORWARDED"))
                .andExpect(jsonPath("$.responsibleSectorName").value(sectorName));
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
    void caseFileEndpointsExposeAggregateAndEnforceVersionedWrites() throws Exception {
        String actor = "case-file-" + UUID.randomUUID();
        String submitter = "submitter-" + actor;
        var receptionist = user(actor).roles("RECEPTIONIST");
        String created = mockMvc.perform(post("/submissions")
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Case file API test",
                                  "purpose": "Verify the W4 case file",
                                  "problemAddressed": "The aggregate and writes need HTTP coverage",
                                  "submitterId": "%s"
                                }
                                """.formatted(submitter)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(created);
        assertTrue(idMatcher.find(), "Create response should include the submission id.");
        String id = idMatcher.group(1);

        mockMvc.perform(post("/submissions/{id}/register", id).with(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        String document = mockMvc.perform(post("/submissions/{id}/documents", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Case file brief",
                                  "status": "SUBMITTED",
                                  "fileReference": "case_file_brief"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher documentIdMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(document);
        assertTrue(documentIdMatcher.find(), "Document response should include its id.");
        String documentId = documentIdMatcher.group(1);

        mockMvc.perform(get("/cases/{id}", id).with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("RECEIVED"))
                .andExpect(jsonPath("$.stepper.length()").value(7))
                .andExpect(jsonPath("$.outcome.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.reviewPanel.availability").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.activity.availability").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.capabilities.nextStep.enabled").value(false));
        mockMvc.perform(get("/cases/{id}/capabilities", id)
                        .with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saveOutcome.enabled").value(false))
                .andExpect(jsonPath("$.saveOutcome.reason").isNotEmpty());
        mockMvc.perform(get("/cases/{id}/transitions/allowed", id)
                        .with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions").isEmpty());

        mockMvc.perform(get("/cases/{id}/transitions/allowed", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions[0]").value("REGISTERED"));
        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(user("officer").roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_stage":"REGISTERED","version":2}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_stage":"REGISTERED","note":"Intake checked","version":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("REGISTERED"))
                .andExpect(jsonPath("$.submission.version").value(3))
                .andExpect(jsonPath("$.stageHistory[-1:].note").value("Intake checked"));

        mockMvc.perform(patch("/cases/{id}/documents/{documentId}", id, documentId)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"VERIFIED","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.submission.version").value(4));

        mockMvc.perform(put("/cases/{id}/outcome", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"Approve","note":"Panel review complete","version":4}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Save stays disabled until the configured reviewer quorum is met"
                ));
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
    void dashboardIsAvailableToAllRolesAndHidesFinanceFromNonFinanceRoles() throws Exception {
        for (String role : new String[] {
                "ADMINISTRATOR", "INNOVATION_COORDINATOR", "RECEPTIONIST",
                "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER",
                "IP_LIAISON", "SUBMITTER", "OBSERVER"
        }) {
            var request = mockMvc.perform(get("/dashboard/overview")
                    .with(user("dashboard-" + role).roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.operations.availability").value("NOT_CONFIGURED"))
                    .andExpect(jsonPath("$.recentActivity.availability").value("NOT_CONFIGURED"))
                    .andExpect(jsonPath("$.pipelineAtAGlance.length()").value(6))
                    .andExpect(jsonPath("$.needsAttention.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(5)));
            if (role.equals("ADMINISTRATOR") || role.equals("INNOVATION_COORDINATOR")) {
                request.andExpect(jsonPath("$.finance.availability").value("NOT_CONFIGURED"));
            } else {
                request.andExpect(jsonPath("$.finance").doesNotExist());
            }
        }
    }

    @Test
    void dashboardMetricsRespectSubmitterScopeAndExplainOpenCases() throws Exception {
        String actor = "dashboard-owner-" + UUID.randomUUID();
        String otherSubmitter = "dashboard-other-" + UUID.randomUUID();
        var receptionist = user("dashboard-intake").roles("RECEPTIONIST");
        String created = mockMvc.perform(post("/submissions")
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Dashboard attention case",
                                  "purpose": "Exercise scoped dashboard metrics",
                                  "problemAddressed": "Dashboard metrics should respect row visibility",
                                  "submitterId": "%s"
                                }
                                """.formatted(actor)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(created);
        assertTrue(idMatcher.find(), "Create response should include the submission id.");
        String id = idMatcher.group(1);
        mockMvc.perform(post("/submissions/{id}/register", id).with(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(user("dashboard-coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_stage":"REGISTERED","version":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.version").value(2));
        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(user("dashboard-coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_stage":"FACT_FINDING","version":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.version").value(3));

        mockMvc.perform(get("/dashboard/overview").with(user(actor).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubmissions").value(1))
                .andExpect(jsonPath("$.studentSubmissions").value(0))
                .andExpect(jsonPath("$.generalSubmissions").value(1))
                .andExpect(jsonPath("$.awaitingReviewCount").value(1))
                .andExpect(jsonPath("$.pipelineAtAGlance.FACT_FINDING").value(1))
                .andExpect(jsonPath("$.needsAttention.length()").value(1))
                .andExpect(jsonPath("$.needsAttention[0].id").value(id))
                .andExpect(jsonPath("$.needsAttention[0].reason").value("Assessment in progress"));

        mockMvc.perform(get("/dashboard/overview").with(user(otherSubmitter).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSubmissions").value(0))
                .andExpect(jsonPath("$.awaitingReviewCount").value(0))
                .andExpect(jsonPath("$.needsAttention").isEmpty());
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
