package org.unipod.unipoderpbackend.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
                                  "submitterId": "%s",
                                  "acceptedRulesVersion": "2026-01"
                                }
                                """.formatted(submitter)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("DRAFT"))
                .andExpect(jsonPath("$.code").isNotEmpty())
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
                .andExpect(jsonPath("$.canCreateBrief").value(true))
                .andExpect(jsonPath("$.canEditDraft").value(true))
                .andExpect(jsonPath("$.canSubmitDraft").value(true))
                .andExpect(jsonPath("$.canRespondToInformationRequest").value(true))
                .andExpect(jsonPath("$.canUploadDocuments").value(true))
                .andExpect(jsonPath("$.canUploadPaymentProof").value(true))
                .andExpect(jsonPath("$.canBook").value(true))
                .andExpect(jsonPath("$.modules.length()").value(4))
                .andExpect(jsonPath("$.modules[?(@.key == 'INVOICES')].availability")
                        .value(org.hamcrest.Matchers.hasItem("AVAILABLE")))
                .andExpect(jsonPath("$.modules[?(@.key == 'BOOKINGS')].availability")
                        .value(org.hamcrest.Matchers.hasItem("AVAILABLE")));

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
                                  "title": "Submitter self-service intake",
                                  "purpose": "Submitters may create their own cases",
                                  "problemAddressed": "The authenticated owner must be used",
                                  "submitterId": "spoofed-owner"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("DRAFT"))
                .andExpect(jsonPath("$.submitterId").value(submitter))
                .andExpect(jsonPath("$.registeredBy").value(submitter));

        mockMvc.perform(get("/submissions")
                        .with(receptionist)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(post("/submissions/{id}/register", id).with(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SUBMITTED"))
                .andExpect(jsonPath("$.code").isNotEmpty());

        mockMvc.perform(post("/submissions/{id}/documents", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Project brief",
                                  "documentType": "PROJECT_BRIEF",
                                  "status": "SUBMITTED",
                                  "fileReference": "brief_012345"
                                }
                                """))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.fileReference").value("brief_012345"))
                        .andExpect(jsonPath("$.documentType").value("PROJECT_BRIEF"));

        String sectorName = "Test sector " + UUID.randomUUID();
        String sectorId = sectors.create(new SectorRequest(
                sectorName,
                "sector@example.invalid",
                true
        )).id().toString();
        completeReceptionChecklist(id, receptionist);
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
                        "SELECT payload ->> 'notificationEmail' FROM notification_outbox "
                                + "WHERE aggregate_id = ? AND event_type = 'submission.forwarded'",
                        String.class,
                        UUID.fromString(id)
                )
        );

        mockMvc.perform(post("/submissions/{id}/confirm-project", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("CONFIRMED_AS_PROJECT"))
                .andExpect(jsonPath("$.projectCode").isNotEmpty());

        mockMvc.perform(post("/submissions/{id}/transition", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("IN_PROGRESS"));

        mockMvc.perform(post("/submissions/{id}/transition", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("COMPLETED"));

        mockMvc.perform(get("/submissions/{id}", id).with(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.id").value(id))
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.stageHistory.length()").value(6));

        mockMvc.perform(get("/queues/reception")
                        .with(receptionist)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mockMvc.perform(get("/dashboard/overview").with(receptionist))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitterHomeExposesPendingWorkAndFiltersTabs() throws Exception {
        String submitter = "home-" + UUID.randomUUID() + "@example.test";
        var submitterUser = user(submitter).roles("SUBMITTER");
        var receptionist = user("reception-home-" + UUID.randomUUID()).roles("RECEPTIONIST");

        String draftId = idOf(mockMvc.perform(post("/submissions")
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Draft on my home",
                                  "purpose": "Keep working on the brief",
                                  "problemAddressed": "The submitter must continue the draft"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String serviceId = idOf(mockMvc.perform(post("/submissions")
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "service_request",
                                  "title": "Prototype request",
                                  "purpose": "Request prototyping support",
                                  "problemAddressed": "The lab needs a prototype",
                                  "serviceRequested": "Prototype build",
                                  "expectedResult": "A working prototype"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String awaitingId = idOf(mockMvc.perform(post("/submissions")
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Awaiting information",
                                  "purpose": "Verify pending work on the home screen",
                                  "problemAddressed": "Reception needs more information",
                                  "submitterEmail": "%s",
                                  "submitterPhone": "+256700000000",
                                  "acceptedRulesVersion": "2026-01"
                                }
                                """.formatted(submitter)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());

        mockMvc.perform(post("/submissions/{id}/submit", awaitingId).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SUBMITTED"));

        mockMvc.perform(post("/submissions/{id}/request-information", awaitingId)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"label": "Recommendation letter", "documentType": "RECOMMENDATION_LETTER",
                                     "message": "Upload a signed recommendation letter."},
                                    {"label": "Supervisor contact"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AWAITING_INFORMATION"));

        mockMvc.perform(get("/me/submissions")
                        .with(submitterUser)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + draftId + "')].pendingAction",
                        org.hamcrest.Matchers.hasItem("CONTINUE_DRAFT")))
                .andExpect(jsonPath("$.items[?(@.id == '" + draftId + "')].waitingForYou",
                        org.hamcrest.Matchers.hasItem("Continue your draft and submit it.")))
                .andExpect(jsonPath("$.items[?(@.id == '" + awaitingId + "')].pendingAction",
                        org.hamcrest.Matchers.hasItem("PROVIDE_DOCUMENT")))
                .andExpect(jsonPath("$.items[?(@.id == '" + awaitingId + "')].waitingForYou",
                        org.hamcrest.Matchers.hasItem("Upload a signed recommendation letter.")))
                .andExpect(jsonPath("$.items[?(@.id == '" + awaitingId + "')].waitingSince")
                        .isNotEmpty());

        mockMvc.perform(get("/me/submissions/{id}", awaitingId).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction").value("PROVIDE_DOCUMENT"))
                .andExpect(jsonPath("$.waitingForYou")
                        .value("Upload a signed recommendation letter."))
                .andExpect(jsonPath("$.waitingSince").isNotEmpty());

        mockMvc.perform(get("/me/submissions")
                        .with(submitterUser)
                        .param("tab", "BRIEFS")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + draftId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + serviceId + "')]").isEmpty());

        mockMvc.perform(get("/me/submissions")
                        .with(submitterUser)
                        .param("tab", "SERVICE_REQUESTS")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + serviceId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + draftId + "')]").isEmpty());

        mockMvc.perform(get("/me/submissions")
                        .with(submitterUser)
                        .param("tab", "service-requests")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + serviceId + "')]").isNotEmpty());

        mockMvc.perform(get("/me/submissions")
                        .with(submitterUser)
                        .param("tab", "receipts"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/me/submissions")
                        .with(submitterUser)
                        .param("tab", "")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + draftId + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.id == '" + serviceId + "')]").isNotEmpty());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/submissions")
                            .with(submitterUser)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "category": "service_request",
                                      "title": "Paged prototype request %s",
                                      "purpose": "Verify tab pagination",
                                      "problemAddressed": "The list must paginate per tab",
                                      "serviceRequested": "Prototype build",
                                      "expectedResult": "A working prototype"
                                    }
                                    """.formatted(i)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/me/submissions")
                        .with(submitterUser)
                        .param("tab", "SERVICE_REQUESTS")
                        .param("size", "2")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/me/submissions")
                        .with(submitterUser)
                        .param("tab", "SERVICE_REQUESTS")
                        .param("size", "2")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(post("/submissions/{id}/documents", awaitingId)
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Recommendation letter",
                                  "documentType": "RECOMMENDATION_LETTER",
                                  "status": "SUBMITTED",
                                  "fileReference": "home_recommendation_letter"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/me/submissions/{id}", awaitingId).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction").value("PROVIDE_INFORMATION"))
                .andExpect(jsonPath("$.waitingForYou").value("Reception needs: Supervisor contact."));

        mockMvc.perform(post("/submissions/{id}/reply", awaitingId)
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SUBMITTED"));

        mockMvc.perform(get("/me/submissions/{id}", awaitingId).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.waitingForYou")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.waitingSince")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void improvementRequestCarriesFilesNotifiesTheSubmitterAndLinksThePendingAction() throws Exception {
        String submitter = "revision-" + UUID.randomUUID() + "@example.test";
        var submitterUser = user(submitter).roles("SUBMITTER");
        var receptionist = user("reception-revision-" + UUID.randomUUID()).roles("RECEPTIONIST");
        var coordinator = user("coordinator-revision-" + UUID.randomUUID())
                .roles("INNOVATION_COORDINATOR");
        var officer = user("officer-revision-" + UUID.randomUUID()).roles("FACT_FINDING_OFFICER");

        String created = mockMvc.perform(post("/submissions")
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Revision upload test",
                                  "purpose": "Verify the improvement module integration",
                                  "problemAddressed": "An improved version must carry files",
                                  "submitterId": "%s",
                                  "acceptedRulesVersion": "2026-01"
                                }
                                """.formatted(submitter)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = idOf(created);

        mockMvc.perform(post("/submissions/{id}/register", id).with(receptionist))
                .andExpect(status().isOk());
        completeReceptionChecklist(id, receptionist);
        var sector = sectors.create(new SectorRequest(
                "Revision sector " + UUID.randomUUID(),
                "revision-sector@example.invalid",
                true
        ));
        mockMvc.perform(post("/submissions/{id}/forward", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":\"" + sector.id() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("FORWARDED"));

        // A fact-finding officer starts fact-finding from FORWARDED; requesting
        // improvement before that is not offered and is rejected as a role error
        // rather than a 409 from the domain transition map.
        mockMvc.perform(get("/cases/{id}/transitions/allowed", id).with(officer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions.length()").value(1))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("IN_FACT_FINDING"));
        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(officer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to_stage\":\"IMPROVEMENT_REQUESTED\",\"note\":"
                                + "\"Skip fact finding\",\"version\":"
                                + caseVersion(id, coordinator) + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to_stage\":\"IN_FACT_FINDING\",\"version\":"
                                + caseVersion(id, coordinator) + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to_stage\":\"IMPROVEMENT_REQUESTED\",\"note\":"
                                + "\"Add field data\",\"version\":"
                                + caseVersion(id, coordinator) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.stage").value("IMPROVEMENT_REQUESTED"));

        // The pending action comes from the transition path, which does not set
        // outcomeNote, so the link is what carries the guidance and due date.
        mockMvc.perform(get("/me/submissions/{id}", id).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("IMPROVEMENT_REQUESTED"))
                .andExpect(jsonPath("$.pendingAction").value("PROVIDE_INFORMATION"))
                .andExpect(jsonPath("$.pendingActionHref")
                        .value("/cases/" + id + "/my-improvement"))
                .andExpect(jsonPath("$.waitingForYou").value(
                        "Reviewers requested an improved version. "
                                + "Open the improvement request for the guidance and due date."));

        mockMvc.perform(post("/submissions/{id}/documents", id)
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Revised brief",
                                  "documentType": "PROJECT_BRIEF",
                                  "status": "SUBMITTED",
                                  "fileReference": "improved_brief"
                                }
                                """))
                .andExpect(status().isCreated());

        String replied = mockMvc.perform(post("/submissions/{id}/reply", id)
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("RESUBMITTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(
                "Your improved version has been received and is ready for the next review.",
                jdbcTemplate.queryForObject(
                        "SELECT payload ->> 'message' FROM notification_outbox "
                                + "WHERE aggregate_id = ? AND event_type = 'submission.reply_received' "
                                + "AND payload ->> 'notificationEmail' = ?",
                        String.class,
                        UUID.fromString(id),
                        submitter
                )
        );

        // Asking for more changes straight after a RESUBMITTED version needs no
        // extra accept step.
        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to_stage\":\"IMPROVEMENT_REQUESTED\",\"note\":"
                                + "\"Still missing field data\",\"version\":"
                                + versionOf(replied) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.stage").value("IMPROVEMENT_REQUESTED"));
    }

    @Test
    void submitterCanCompleteRequestedDraftAndReceptionCanForwardItAtomically() throws Exception {
        String submitter = "owner-" + UUID.randomUUID() + "@example.test";
        String receptionist = "reception-" + UUID.randomUUID();
        var submitterUser = user(submitter).roles("SUBMITTER");
        var receptionistUser = user(receptionist).roles("RECEPTIONIST");

        String created = mockMvc.perform(post("/submissions")
                        .with(receptionistUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "student",
                                  "title": "Submitter intake test",
                                  "purpose": "Complete the requested student details",
                                  "problemAddressed": "Validate submitter ownership and intake",
                                  "submitterId": "%s"
                                }
                                """.formatted(submitter)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("DRAFT"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("BRF-")))
                .andExpect(jsonPath("$.submitterId").value(submitter))
                .andExpect(jsonPath("$.registeredBy").value(receptionist))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(created);
        Matcher codeMatcher = Pattern.compile("\"code\":\"([^\"]+)\"").matcher(created);
        assertTrue(idMatcher.find(), "Create response should include the submission id.");
        assertTrue(codeMatcher.find(), "Create response should include the tracking reference.");
        String id = idMatcher.group(1);
        String code = codeMatcher.group(1);

        mockMvc.perform(post("/submissions/{id}/request-information", id)
                        .with(receptionistUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Please provide your student number and supervisor."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.informationRequestedNote")
                        .value("Please provide your student number and supervisor."))
                .andExpect(jsonPath("$.informationRequestedBy").doesNotExist());

        mockMvc.perform(get("/me/submissions/{id}", id).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction").value("PROVIDE_INFORMATION"))
                .andExpect(jsonPath("$.waitingForYou")
                        .value("Please provide your student number and supervisor."))
                .andExpect(jsonPath("$.waitingSince").isNotEmpty());

        mockMvc.perform(get("/submissions/search")
                        .with(submitterUser)
                        .param("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].informationRequestedNote")
                        .value("Please provide your student number and supervisor."))
                .andExpect(jsonPath("$.items[0].informationRequestedBy").doesNotExist());

        mockMvc.perform(post("/submissions/{id}/documents", id)
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Recommendation letter",
                                  "documentType": "RECOMMENDATION_LETTER",
                                  "status": "SUBMITTED",
                                  "fileReference": "recommendation_123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("RECOMMENDATION_LETTER"));

        String completedDraft = """
                {
                  "category": "student",
                  "title": "Completed student intake",
                  "purpose": "Provide the requested information",
                  "problemAddressed": "Validate that resubmission clears the request",
                  "submitterId": "spoofed-owner",
                  "studentNumber": "STU-12345",
                  "programme": "Engineering",
                  "supervisor": "Dr Example",
                  "recommendationLetterStatus": "SUBMITTED",
                  "acceptedRulesVersion": "2026-01"
                }
                """;
        mockMvc.perform(put("/submissions/{id}", id)
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedDraft))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.submitterId").value(submitter))
                .andExpect(jsonPath("$.informationRequestedNote").doesNotExist())
                .andExpect(jsonPath("$.informationRequestedBy").doesNotExist());

        mockMvc.perform(put("/submissions/{id}", id)
                        .with(user("another-owner").roles("SUBMITTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completedDraft))
                .andExpect(status().isForbidden());

        var sector = sectors.create(new SectorRequest(
                "Atomic forward " + UUID.randomUUID(),
                "atomic-forward@example.invalid",
                true
        ));
        completeReceptionChecklist(id, receptionistUser);
        mockMvc.perform(post("/submissions/{id}/forward", id)
                        .with(receptionistUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":\"" + sector.id() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("FORWARDED"))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty())
                .andExpect(jsonPath("$.responsibleSectorId").value(sector.id().toString()));

        mockMvc.perform(get("/me/submissions/{id}", id).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.waitingForYou").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.waitingSince").value(org.hamcrest.Matchers.nullValue()));

        UUID submissionId = UUID.fromString(id);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE submission_id = ? AND document_type = 'RECOMMENDATION_LETTER'",
                Integer.class,
                submissionId
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE aggregate_id = ? "
                        + "AND event_type = 'submission.information_requested'",
                Integer.class,
                submissionId
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outbox WHERE aggregate_id = ? "
                        + "AND event_type = 'submission.forwarded'",
                Integer.class,
                submissionId
        ));
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM case_stage_history WHERE submission_id = ?",
                Integer.class,
                submissionId
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE actor = ? AND http_method = 'PUT' "
                        + "AND response_status = 403 AND request_path LIKE '%/submissions/%'",
                Integer.class,
                "another-owner"
        ));
    }

    @Test
    void unauthenticatedWaluubeRequestsAreAudited() throws Exception {
        mockMvc.perform(get("/submissions"))
                .andExpect(status().isUnauthorized());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE actor = 'anonymous' "
                        + "AND http_method = 'GET' AND request_path LIKE '%/submissions' "
                        + "AND response_status = 401",
                Integer.class
        ));
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
                                  "submitterId": "%s",
                                  "acceptedRulesVersion": "2026-01"
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

        mockMvc.perform(post("/submissions/{id}/missing-items", id)
                        .with(user("officer").roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [{
                                    "label": "Supervisor contact",
                                    "reason": "Missing detail",
                                    "dueAt": "2026-11-10T09:00:00Z",
                                    "message": "Please provide a contact number"
                                  }]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/cases/{id}", id).with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("SUBMITTED"))
                .andExpect(jsonPath("$.stepper.length()").value(9))
                .andExpect(jsonPath("$.outcome.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.openRequests[0].label").value("Supervisor contact"))
                .andExpect(jsonPath("$.openRequests[0].dueAt").isNotEmpty())
                .andExpect(jsonPath("$.reviewPanel.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.reviewPanel.quorumAvailable").value(false))
                .andExpect(jsonPath("$.reviewPanel.ipPanel").doesNotExist())
                .andExpect(jsonPath("$.activity.availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.activity.items").isArray())
                .andExpect(jsonPath("$.capabilities.nextStep.enabled").value(false));
        mockMvc.perform(get("/cases/{id}", id).with(user("ip-liaison").roles("IP_LIAISON")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/cases/{id}", id).with(user("observer").roles("OBSERVER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/cases/{id}/activity", id)
                        .with(user(submitter).roles("SUBMITTER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/cases/{id}/activity", id)
                        .with(user("ip-liaison").roles("IP_LIAISON")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/cases/{id}/activity", id)
                        .with(user("observer").roles("OBSERVER")))
                .andExpect(status().isForbidden());
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
                .andExpect(jsonPath("$.allowedTransitions").isEmpty());
        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(user("officer").roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_stage":"FORWARDED","version":3}
                                """))
                .andExpect(status().isForbidden());

        var forwardSector = sectors.create(new SectorRequest(
                "Case file forward " + UUID.randomUUID(),
                "case-file-forward@example.invalid",
                true
        ));
        completeReceptionChecklist(id, receptionist);
        mockMvc.perform(post("/submissions/{id}/forward", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":\"" + forwardSector.id()
                                + "\",\"note\":\"Intake checked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("FORWARDED"))
                .andExpect(jsonPath("$.version").value(5));

        mockMvc.perform(get("/cases/{id}", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("FORWARDED"))
                .andExpect(jsonPath("$.stageHistory[-1:].note").value("Intake checked"));

        mockMvc.perform(patch("/cases/{id}/documents/{documentId}", id, documentId)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"VERIFIED","version":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents[0].status").value("VERIFIED"))
                .andExpect(jsonPath("$.submission.version").value(6));

        mockMvc.perform(put("/cases/{id}/outcome", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"Needs more improvement","note":"Panel review complete","version":6}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Save stays disabled until the configured reviewer quorum is met"
                ));

        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_stage":"IN_FACT_FINDING","version":-1}
                                """))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(put("/cases/{id}/outcome", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"Needs more improvement","version":-1}
                                """))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(put("/cases/{id}/outcome", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"Approve","note":"Panel review complete","version":6}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Submission details are invalid or incomplete."));
        mockMvc.perform(put("/cases/{id}/outcome", id)
                        .with(user("coordinator").roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":" ","note":"  ","version":6}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("One or more request values are invalid."));
    }

    @Test
    void intakeRoleCanReadQueueAndManagersCanReadDashboard() throws Exception {
        mockMvc.perform(get("/queues/reception").with(user("receptionist").roles("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.inQueueCount").isNumber())
                .andExpect(jsonPath("$.awaitingActionCount").isNumber())
                .andExpect(jsonPath("$.recentlyUpdatedCount").isNumber());

        mockMvc.perform(get("/dashboard/overview")
                        .with(user("dashboard-coordinator-" + UUID.randomUUID())
                                .roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBriefs").isNumber())
                .andExpect(jsonPath("$.pipeline.length()").value(6))
                .andExpect(jsonPath("$.completedThisMonth").isNumber());
    }

    @Test
    void dashboardIsRestrictedToManagersAndCoordinators() throws Exception {
        for (String role : new String[] {
                "RECEPTIONIST", "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER",
                "IP_LIAISON", "SUBMITTER", "OBSERVER"
        }) {
            mockMvc.perform(get("/dashboard/overview")
                            .with(user("dashboard-denied-" + role).roles(role)))
                    .andExpect(status().isForbidden());
        }

        for (String role : new String[] {"ADMINISTRATOR", "INNOVATION_COORDINATOR"}) {
            mockMvc.perform(get("/dashboard/overview")
                            .with(user("dashboard-manager-" + role).roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalBriefs").isNumber())
                    .andExpect(jsonPath("$.waitingForReview").isNumber())
                    .andExpect(jsonPath("$.needingImprovement").isNumber())
                    .andExpect(jsonPath("$.completedThisMonth").isNumber())
                    .andExpect(jsonPath("$.pipeline.length()").value(6))
                    .andExpect(jsonPath("$.needsAttention.length()")
                            .value(org.hamcrest.Matchers.lessThanOrEqualTo(5)))
                    .andExpect(jsonPath("$.overdueEquipmentReturns.availability")
                            .value("NOT_CONFIGURED"))
                    .andExpect(jsonPath("$.recentActivity.availability").value("NOT_CONFIGURED"))
                    .andExpect(jsonPath("$.studentSubmissions").doesNotExist())
                    .andExpect(jsonPath("$.byStage").doesNotExist())
                    .andExpect(jsonPath("$.operations").doesNotExist())
                    .andExpect(jsonPath("$.finance").doesNotExist());
        }
    }

    @Test
    void managerDashboardShowsStaleCasesAndOmitsLegacyTiles() throws Exception {
        String actor = "dashboard-owner-" + UUID.randomUUID();
        var intake = user("dashboard-intake-" + UUID.randomUUID()).roles("RECEPTIONIST");
        var manager = user("dashboard-manager-" + UUID.randomUUID()).roles("ADMINISTRATOR");
        String created = mockMvc.perform(post("/submissions")
                        .with(intake)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Dashboard attention case",
                                  "purpose": "Exercise manager dashboard metrics",
                                  "problemAddressed": "Stale cases should need attention",
                                  "submitterId": "%s",
                                  "acceptedRulesVersion": "2026-01"
                                }
                                """.formatted(actor)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(created);
        assertTrue(idMatcher.find(), "Create response should include the submission id.");
        String id = idMatcher.group(1);
        mockMvc.perform(post("/submissions/{id}/register", id).with(intake))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        var attentionSector = sectors.create(new SectorRequest(
                "Dashboard attention sector " + UUID.randomUUID(),
                "dashboard-attention@example.invalid",
                true
        ));
        completeReceptionChecklist(id, intake);
        mockMvc.perform(post("/submissions/{id}/forward", id)
                        .with(intake)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectorId\":\"" + attentionSector.id() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("FORWARDED"))
                .andExpect(jsonPath("$.version").value(3));
        mockMvc.perform(post("/cases/{id}/transitions", id)
                        .with(manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_stage":"IN_FACT_FINDING","version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission.version").value(4));

        jdbcTemplate.update("UPDATE submissions SET updated_at = CURRENT_TIMESTAMP WHERE id <> ?",
                UUID.fromString(id));
        jdbcTemplate.update(
                "UPDATE submissions SET updated_at = CURRENT_TIMESTAMP - INTERVAL '20 days' WHERE id = ?",
                UUID.fromString(id));

        mockMvc.perform(get("/dashboard/overview").with(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBriefs").isNumber())
                .andExpect(jsonPath("$.needingImprovement").isNumber())
                .andExpect(jsonPath("$.completedThisMonth").isNumber())
                .andExpect(jsonPath("$.pipeline.FACT_FINDING").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.needsAttention.length()").value(1))
                .andExpect(jsonPath("$.needsAttention[0].id").value(id))
                .andExpect(jsonPath("$.needsAttention[0].reason").value("Fact-finding in progress"))
                .andExpect(jsonPath("$.overdueEquipmentReturns.availability").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.recentActivity.availability").value("NOT_CONFIGURED"));

        jdbcTemplate.update("UPDATE submissions SET stage = 'READY_FOR_REVIEW' WHERE id = ?",
                UUID.fromString(id));

        var reviewManager = user("dashboard-review-manager-" + UUID.randomUUID())
                .roles("ADMINISTRATOR");
        mockMvc.perform(get("/dashboard/overview").with(reviewManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingForReview").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.pipeline.UNDER_REVIEW").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void managerDashboardExcludesDraftsFromTotalBriefs() throws Exception {
        String actor = "dashboard-draft-owner-" + UUID.randomUUID();
        var intake = user("dashboard-draft-intake-" + UUID.randomUUID()).roles("RECEPTIONIST");
        var beforeManager = user("dashboard-draft-before-" + UUID.randomUUID()).roles("ADMINISTRATOR");
        var afterManager = user("dashboard-draft-after-" + UUID.randomUUID()).roles("ADMINISTRATOR");

        long before = dashboardTotal(beforeManager);

        mockMvc.perform(post("/submissions")
                        .with(intake)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Draft excluded from totals",
                                  "purpose": "Verify drafts do not inflate dashboard totals",
                                  "problemAddressed": "Drafts are work in progress",
                                  "submitterId": "%s"
                                }
                                """.formatted(actor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("DRAFT"));

        assertEquals(before, dashboardTotal(afterManager),
                "Draft briefs must not be counted in the manager dashboard total.");
    }

    @Test
    void databaseRejectsInconsistentReceivedAndForwardedMetadata() {
        org.springframework.dao.DataIntegrityViolationException receivedWithSector = org.junit.jupiter.api.Assertions
                .assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                        jdbcTemplate.update("""
                                INSERT INTO submissions (
                                    id, category, title, purpose, problem_addressed, registered_by,
                                    submitter_id, stage, code, received_at, receiving_officer,
                                    responsible_sector_id, forwarded_at, created_at, updated_at
                                ) VALUES (?, 'GENERAL', 'Invalid received', 'Purpose', 'Problem',
                                    'actor', 'submitter', 'SUBMITTED', 'BRF-2026-900001',
                                    CURRENT_TIMESTAMP, 'officer', ?, CURRENT_TIMESTAMP,
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                                """, UUID.randomUUID(), UUID.randomUUID())
                );
        assertTrue(receivedWithSector.getMostSpecificCause().getMessage().contains(
                "ck_submission_receipt_state"
        ));
    }

    @Test
    void canonicalForwardedCaseDoesNotRequireLegacySectorMetadata() {
        assertEquals(1, jdbcTemplate.update("""
                INSERT INTO submissions (
                    id, category, title, purpose, problem_addressed, registered_by,
                    submitter_id, stage, code, received_at, receiving_officer,
                    created_at, updated_at
                ) VALUES (?, 'GENERAL', 'Canonical forwarded', 'Purpose', 'Problem',
                    'actor', 'submitter', 'FORWARDED', 'BRF-2026-900002',
                    CURRENT_TIMESTAMP, 'officer', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID()));
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
    void sectorFilterOptionsAreAvailableToAllSubmissionReadersWithoutContactDetails() throws Exception {
        String name = "Filter sector " + UUID.randomUUID();
        var sector = sectors.create(new SectorRequest(name, "private@example.invalid", true));

        mockMvc.perform(get("/sectors/filter-options").with(user("observer").roles("OBSERVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + sector.id() + "')].name").value(name))
                .andExpect(jsonPath("$[?(@.id == '" + sector.id() + "')].notificationEmail").isEmpty());
    }

    @Test
    void receptionistCannotAdvanceLifecycleStages() throws Exception {
        mockMvc.perform(post("/submissions/{id}/transition", UUID.randomUUID())
                        .with(user("receptionist").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
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
                .andExpect(status().isUnprocessableContent())
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
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void searchMatchesReferenceTitleAndSubmitterName() throws Exception {
        String token = UUID.randomUUID().toString();
        String title = "Solar tracker " + token;
        String submitterName = "Amina Nsubuga " + token;
        var receptionist = user("reception-" + token).roles("RECEPTIONIST");
        var observer = user("observer-" + token).roles("OBSERVER");

        String created = mockMvc.perform(post("/submissions")
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "%s",
                                  "purpose": "Verify search fields",
                                  "problemAddressed": "Search must cover every display key",
                                  "submitterId": "submitter-%s",
                                  "submitterName": "%s",
                                  "submitterEmail": "submitter-%s@example.test",
                                  "acceptedRulesVersion": "2026-01"
                                }
                                """.formatted(title, token, submitterName, token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submitterName").value(submitterName))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(created);
        Matcher codeMatcher = Pattern.compile("\"code\":\"([^\"]+)\"").matcher(created);
        assertTrue(idMatcher.find(), "Create response should include the submission id.");
        assertTrue(codeMatcher.find(), "Create response should include the tracking reference.");
        String id = idMatcher.group(1);
        String code = codeMatcher.group(1);

        mockMvc.perform(post("/submissions/" + id + "/submit")
                        .with(receptionist))
                .andExpect(status().isOk());

        mockMvc.perform(get("/submissions/search")
                        .with(observer)
                        .param("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));

        mockMvc.perform(get("/submissions/search")
                        .with(observer)
                        .param("code", code.toLowerCase(Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));

        mockMvc.perform(get("/submissions/search")
                        .with(observer)
                        .param("code", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty());

        mockMvc.perform(get("/submissions/search")
                        .with(observer)
                        .param("code", "Amina Nsubuga " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));

        mockMvc.perform(get("/submissions")
                        .with(observer)
                        .param("q", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty());
    }

    @Test
    void searchMatchesConfirmedProjectByProjectCode() throws Exception {
        String token = UUID.randomUUID().toString();
        var receptionist = user("reception-project-" + token).roles("RECEPTIONIST");
        var observer = user("observer-project-" + token).roles("OBSERVER");

        String created = mockMvc.perform(post("/submissions")
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Project code search %s",
                                  "purpose": "Verify project-code search",
                                  "problemAddressed": "Search must find confirmed projects",
                                  "submitterId": "submitter-%s",
                                  "submitterEmail": "project-search-%s@example.test",
                                  "acceptedRulesVersion": "2026-01"
                                }
                                """.formatted(token, token, token)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = idOf(created);

        mockMvc.perform(post("/submissions/{id}/submit", id).with(receptionist))
                .andExpect(status().isOk());
        completeReceptionChecklist(id, receptionist);

        String confirmed = mockMvc.perform(post("/submissions/{id}/confirm-project", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("CONFIRMED_AS_PROJECT"))
                .andExpect(jsonPath("$.projectCode").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher projectCodeMatcher = Pattern.compile("\"projectCode\":\"([^\"]+)\"").matcher(confirmed);
        assertTrue(projectCodeMatcher.find(), "Confirm response should include the project code.");
        String projectCode = projectCodeMatcher.group(1);

        mockMvc.perform(get("/submissions/search")
                        .with(observer)
                        .param("code", projectCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].projectCode").value(projectCode));

        mockMvc.perform(get("/submissions/search")
                        .with(observer)
                        .param("code", projectCode.toLowerCase(Locale.ROOT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty());

        mockMvc.perform(get("/submissions")
                        .with(observer)
                        .param("q", projectCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty());
    }

    @Test
    void submissionListFiltersByInclusiveReceivedDateRange() throws Exception {
        String token = UUID.randomUUID().toString();
        var receptionist = user("reception-" + token).roles("RECEPTIONIST");
        var observer = user("observer-" + token).roles("OBSERVER");
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Kampala"));

        String created = mockMvc.perform(post("/submissions")
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Dated case %s",
                                  "purpose": "Verify the received date filter",
                                  "problemAddressed": "Reception needs date filtering",
                                  "submitterId": "submitter-%s",
                                  "acceptedRulesVersion": "2026-01"
                                }
                                """.formatted(token, token)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(created);
        assertTrue(idMatcher.find(), "Create response should include the submission id.");
        String id = idMatcher.group(1);

        mockMvc.perform(post("/submissions/{id}/register", id).with(receptionist))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());

        mockMvc.perform(get("/submissions")
                        .with(observer)
                        .param("q", token)
                        .param("receivedFrom", today.toString())
                        .param("receivedTo", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + id + "')]").isNotEmpty());

        mockMvc.perform(get("/submissions")
                        .with(observer)
                        .param("q", token)
                        .param("receivedFrom", today.plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/submissions")
                        .with(observer)
                        .param("q", token)
                        .param("receivedTo", today.minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/submissions")
                        .with(observer)
                        .param("receivedFrom", today.toString())
                        .param("receivedTo", today.minusDays(1).toString()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void updatingAndSubmittingADraftClearsInformationItems() throws Exception {
        String submitter = "draft-items-" + UUID.randomUUID() + "@example.test";
        var submitterUser = user(submitter).roles("SUBMITTER");
        var receptionist = user("reception-draft-items-" + UUID.randomUUID()).roles("RECEPTIONIST");

        String created = mockMvc.perform(post("/submissions")
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Draft with requested items",
                                  "purpose": "Verify item resolution on draft update",
                                  "problemAddressed": "Reception needs one more detail",
                                  "submitterId": "%s",
                                  "submitterEmail": "%s",
                                  "acceptedRulesVersion": "2026-01"
                                }
                                """.formatted(submitter, submitter)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = idOf(created);

        mockMvc.perform(post("/submissions/{id}/request-information", id)
                        .with(receptionist)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"label":"Student number"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DRAFT"));

        mockMvc.perform(get("/me/submissions/{id}", id).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction").value("PROVIDE_INFORMATION"))
                .andExpect(jsonPath("$.waitingForYou").value("Reception needs: Student number."));

        mockMvc.perform(put("/submissions/{id}", id)
                        .with(submitterUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "general",
                                  "title": "Draft with requested items completed",
                                  "purpose": "Verify item resolution on draft update",
                                  "problemAddressed": "Reception needs one more detail"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/submissions/{id}/submit", id).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("SUBMITTED"));

        mockMvc.perform(get("/me/submissions/{id}", id).with(submitterUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingAction").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.waitingForYou").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.waitingSince").value(org.hamcrest.Matchers.nullValue()));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM missing_items WHERE submission_id = ? "
                        + "AND kind = 'INFORMATION' AND status = 'OPEN'",
                Integer.class,
                UUID.fromString(id)
        ));
    }

    private void completeReceptionChecklist(String id, RequestPostProcessor principal) throws Exception {
        mockMvc.perform(put("/submissions/{id}/reception-checklist", id)
                        .with(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"key": "DETAILS_COMPLETE", "checked": true},
                                    {"key": "DOCUMENTS_COMPLETE", "checked": true},
                                    {"key": "FILES_READABLE", "checked": true},
                                    {"key": "CORRECT_TYPE", "checked": true}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
    }

    private long dashboardTotal(RequestPostProcessor principal) throws Exception {
        String body = mockMvc.perform(get("/dashboard/overview").with(principal))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher matcher = Pattern.compile("\"totalBriefs\":(\\d+)").matcher(body);
        assertTrue(matcher.find(), "Dashboard response should include totalBriefs.");
        return Long.parseLong(matcher.group(1));
    }

    private long caseVersion(String id, RequestPostProcessor principal) throws Exception {
        String body = mockMvc.perform(get("/cases/{id}", id).with(principal))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return versionOf(body);
    }

    private static long versionOf(String response) {
        Matcher matcher = Pattern.compile("\"version\":(\\d+)").matcher(response);
        assertTrue(matcher.find(), "Response should include a version.");
        return Long.parseLong(matcher.group(1));
    }

    private static String idOf(String response) {
        Matcher matcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(response);
        assertTrue(matcher.find(), "Response should include the submission id.");
        return matcher.group(1);
    }
}
