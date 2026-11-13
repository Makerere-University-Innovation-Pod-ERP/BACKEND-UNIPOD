package org.unipod.unipoderpbackend.controller.improvement;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefinementRequestApiIntegrationTest {
    static final String ASK = "/cases/{id}/refinement-requests";
    static final String HISTORY = "/cases/{id}/refinement-history";
    static final String COORDINATOR = "coord@example.test";
    static final String OFFICER = "officer@example.test";
    static final String SUBMITTER = "submitter@example.test";
    static final String MARKER = "SECRET-MARKER-xyz";
    static final String CHANGE_MARKER = "CHANGE-MARKER-abc";
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private RefinementRequestRepository requests;

    @Autowired
    private CaseRevisionRepository revisions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- 1-3: asking moves the brief and records the request ------------------------------

    @Test
    void coordinatorAsksOnABriefInReview() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        int historyBefore = historyRows(brief);

        ask(brief, coordinator(), body("Please add a costed budget.", due(14),
                List.of("Add a budget table", "Attach two letters of support", "Clarify the user group"),
                caseVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.cycle").value(1))
                .andExpect(jsonPath("$.source").value("COORDINATOR"))
                .andExpect(jsonPath("$.guidanceNote").value("Please add a costed budget."))
                .andExpect(jsonPath("$.dueDate").value(due(14).toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.requestedBy").value(COORDINATOR))
                .andExpect(jsonPath("$.requestedAt").value(notNullValue()))
                .andExpect(jsonPath("$.respondedAt").value(nullValue()))
                .andExpect(jsonPath("$.requestedChanges", hasSize(3)))
                .andExpect(jsonPath("$.requestedChanges[0].position").value(1))
                .andExpect(jsonPath("$.requestedChanges[0].text").value("Add a budget table"))
                .andExpect(jsonPath("$.requestedChanges[1].position").value(2))
                .andExpect(jsonPath("$.requestedChanges[1].text").value("Attach two letters of support"))
                .andExpect(jsonPath("$.requestedChanges[2].position").value(3))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.briefStage").value("IMPROVEMENT_REQUESTED"));

        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
        assertEquals(historyBefore + 1, historyRows(brief));
        Map<String, Object> history = jdbcTemplate.queryForMap("""
                SELECT from_stage, to_stage, note, changed_by FROM case_stage_history
                WHERE submission_id = ? ORDER BY changed_at DESC LIMIT 1
                """, brief.getId());
        assertEquals("IN_REVIEW", history.get("from_stage"));
        assertEquals("IMPROVEMENT_REQUESTED", history.get("to_stage"));
        assertEquals("Please add a costed budget.", history.get("note"));
        assertEquals(1, refinementRows(brief));
        assertEquals(List.of(1, 2, 3), jdbcTemplate.queryForList("""
                SELECT c.position FROM refinement_requested_changes c
                JOIN refinement_requests r ON r.id = c.request_id WHERE r.brief_id = ? ORDER BY c.position
                """, Integer.class, brief.getId()));
    }

    @Test
    void factFindingOfficerAsksDuringFactFinding() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_FACT_FINDING);

        ask(brief, officer(), body("Clarify the problem statement.", due(7), List.of("Rewrite the problem"),
                caseVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("FACT_FINDING"))
                .andExpect(jsonPath("$.requestedBy").value(OFFICER))
                .andExpect(jsonPath("$.briefStage").value("IMPROVEMENT_REQUESTED"));
        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
    }

    @ParameterizedTest
    @EnumSource(value = SubmissionStage.class, names = {"READY_FOR_REVIEW", "IN_FACT_FINDING"})
    void coordinatorCanAskFromReadyForReviewAndFactFinding(SubmissionStage start) throws Exception {
        Submission brief = briefInStage(start);

        ask(brief, coordinator(), body("Improve it.", due(5), List.of("One change"), caseVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("COORDINATOR"));
        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
    }

    @Test
    void administratorCanAsk() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        ask(brief, user("admin@example.test").roles("ADMINISTRATOR"),
                body("Improve it.", due(5), List.of("One change"), caseVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("COORDINATOR"));
    }

    // ---- 4: officer limits ----------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = SubmissionStage.class, names = {"READY_FOR_REVIEW", "IN_REVIEW"})
    void officerCannotAskAfterFactFinding(SubmissionStage start) throws Exception {
        Submission brief = briefInStage(start);
        Snapshot before = snapshot(brief);

        ask(brief, officer(), body("Improve it.", due(5), List.of("One change"), caseVersion(brief)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        assertEquals(before, snapshot(brief));
    }

    @Test
    void officerOnAForwardedBriefGetsAConflictAndNothingChanges() throws Exception {
        Submission brief = briefInStage(SubmissionStage.FORWARDED);
        Snapshot before = snapshot(brief);

        ask(brief, officer(), body("Improve it.", due(5), List.of("One change"), caseVersion(brief)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Submission conflict"))
                .andExpect(jsonPath("$.detail").value(containsString("Improvement can only be requested")));
        assertEquals(before, snapshot(brief));
    }

    // ---- 5, 6: adopting a brief already marked for improvement ----------------------------

    @ParameterizedTest
    @ValueSource(strings = {"INNOVATION_COORDINATOR", "FACT_FINDING_OFFICER"})
    void adoptsABriefAlreadyMarkedForImprovementWithoutAnotherTransition(String role) throws Exception {
        Submission brief = markedForImprovementThroughTheTransitionEndpoint();
        int historyBefore = historyRows(brief);

        ask(brief, user("adopter@example.test").roles(role),
                body("Here is what we need.", due(10), List.of("First", "Second"), null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cycle").value(1))
                .andExpect(jsonPath("$.source").value(role.equals("FACT_FINDING_OFFICER") ? "FACT_FINDING" : "COORDINATOR"))
                .andExpect(jsonPath("$.briefStage").value("IMPROVEMENT_REQUESTED"));

        assertEquals(historyBefore, historyRows(brief), "adopting must not run another transition");
        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
        assertEquals(1, refinementRows(brief));
    }

    @Test
    void adoptingWhenARequestIsAlreadyOpenIsAConflict() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        ask(brief, coordinator(), body("First ask.", due(5), List.of("One"), caseVersion(brief)))
                .andExpect(status().isCreated());
        Snapshot before = snapshot(brief);

        ask(brief, officer(), body("Second ask.", due(5), List.of("Two"), null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An improvement request is already open for this brief."));
        assertEquals(before, snapshot(brief));
    }

    // ---- 7: a second cycle ----------------------------------------------------------------

    @Test
    void aSecondCycleIsCreatedAfterTheFirstWasAnswered() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        ask(brief, coordinator(), body("First round.", due(5), List.of("One"), caseVersion(brief)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cycle").value(1));

        RefinementRequest first = requests.findTopByBriefIdOrderByCycleDesc(brief.getId()).orElseThrow();
        first.setStatus(RefinementStatus.RESPONDED);
        first.setRespondedAt(Instant.parse("2026-11-20T10:00:00Z"));
        requests.saveAndFlush(first);
        Submission reloaded = submissions.findById(brief.getId()).orElseThrow();
        reloaded.transitionTo(SubmissionStage.RESUBMITTED, Instant.parse("2026-11-20T10:00:00Z"));
        reloaded.transitionTo(SubmissionStage.IN_REVIEW, Instant.parse("2026-11-20T11:00:00Z"));
        submissions.saveAndFlush(reloaded);

        ask(brief, coordinator(), body("Second round.", due(9), List.of("Two", "Three"), caseVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cycle").value(2))
                .andExpect(jsonPath("$.status").value("OPEN"));
        assertEquals(2, refinementRows(brief));
    }

    // ---- 8: case version ------------------------------------------------------------------

    @Test
    void staleCaseVersionIsTheTransitionsConflict() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        long version = caseVersion(brief);
        Snapshot before = snapshot(brief);

        ask(brief, coordinator(), body("Improve it.", due(5), List.of("One"), version + 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This case was changed by another user. Reload it before saving."));
        assertEquals(before, snapshot(brief));
    }

    @Test
    void missingCaseVersionIsUnprocessableWhenATransitionIsNeeded() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        Snapshot before = snapshot(brief);

        ask(brief, coordinator(), body("Improve it.", due(5), List.of("One"), null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("caseVersion")));
        assertEquals(before, snapshot(brief));
    }

    // ---- 9: validation --------------------------------------------------------------------

    @Test
    void invalidFieldsAreRejectedAndNothingIsWritten() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        long version = caseVersion(brief);
        Snapshot before = snapshot(brief);
        List<String> twentyOne = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            twentyOne.add("Item " + i);
        }
        List<String> blankItem = new ArrayList<>(List.of("Fine", "   "));

        Map<Map<String, Object>, String> cases = new LinkedHashMap<>();
        cases.put(body("   ", due(5), List.of("One"), version), "guidanceNote");
        cases.put(body(null, due(5), List.of("One"), version), "guidanceNote");
        cases.put(body("g".repeat(2001), due(5), List.of("One"), version), "guidanceNote");
        cases.put(body("Fine", today().minusDays(1), List.of("One"), version), "dueDate");
        cases.put(body("Fine", null, List.of("One"), version), "dueDate");
        cases.put(body("Fine", due(5), List.of(), version), "requestedChanges");
        cases.put(body("Fine", due(5), null, version), "requestedChanges");
        cases.put(body("Fine", due(5), twentyOne, version), "requestedChanges");
        cases.put(body("Fine", due(5), blankItem, version), "requestedChanges");
        cases.put(body("Fine", due(5), List.of("c".repeat(501)), version), "requestedChanges");
        for (Map.Entry<Map<String, Object>, String> entry : cases.entrySet()) {
            ask(brief, coordinator(), entry.getKey())
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value(containsString(entry.getValue())));
            assertEquals(before, snapshot(brief), entry.getValue());
        }
    }

    @Test
    void limitsAreAcceptedAfterTrimming() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        List<String> twenty = new ArrayList<>();
        for (int i = 1; i <= 19; i++) {
            twenty.add("  Item " + i + "  ");
        }
        twenty.add("  " + "c".repeat(500) + "  ");

        ask(brief, coordinator(), body("  " + "g".repeat(2000) + "  ", today(), twenty, caseVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guidanceNote").value("g".repeat(2000)))
                .andExpect(jsonPath("$.dueDate").value(today().toString()))
                .andExpect(jsonPath("$.requestedChanges", hasSize(20)))
                .andExpect(jsonPath("$.requestedChanges[0].text").value("Item 1"))
                .andExpect(jsonPath("$.requestedChanges[19].text").value("c".repeat(500)));
    }

    // ---- 10: stages that are not allowed --------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = SubmissionStage.class,
            names = {"DRAFT", "SUBMITTED", "RESUBMITTED", "CONFIRMED_AS_PROJECT", "COMPLETED", "CLOSED"})
    void stagesThatAreNotAllowedAreAConflict(SubmissionStage stage) throws Exception {
        Submission brief = briefInStage(stage);
        Snapshot before = snapshot(brief);

        ask(brief, coordinator(), body("Improve it.", due(5), List.of("One"), 0L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("Improvement can only be requested")));
        assertEquals(before, snapshot(brief));
    }

    // ---- 11: security and request shape --------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"REVIEWER", "SUBMITTER", "RECEPTIONIST", "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"})
    void otherRolesAreForbiddenOnBothEndpoints(String role) throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        Snapshot before = snapshot(brief);
        RequestPostProcessor other = user(SUBMITTER).roles(role);

        ask(brief, other, body("Improve it.", due(5), List.of("One"), caseVersion(brief)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Forbidden"));
        mockMvc.perform(get(HISTORY, brief.getId()).with(other))
                .andExpect(status().isForbidden());
        assertEquals(before, snapshot(brief));
    }

    @Test
    void unauthenticatedUnknownAndMalformedRequests() throws Exception {
        mockMvc.perform(withJson(post(ASK, UNKNOWN_BRIEF), body("x", due(5), List.of("x"), 0L)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(HISTORY, UNKNOWN_BRIEF)).andExpect(status().isUnauthorized());
        ask(UNKNOWN_BRIEF, coordinator(), body("x", due(5), List.of("x"), 0L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Submission not found"));
        mockMvc.perform(get(HISTORY, UNKNOWN_BRIEF).with(coordinator())).andExpect(status().isNotFound());
        mockMvc.perform(withJson(post("/cases/not-a-uuid/refinement-requests"), body("x", due(5), List.of("x"), 0L))
                        .with(coordinator()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/cases/not-a-uuid/refinement-history").with(coordinator()))
                .andExpect(status().isBadRequest());
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        mockMvc.perform(post(ASK, brief.getId()).with(coordinator())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"guidanceNote\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
        assertEquals("IN_REVIEW", stage(brief));
    }

    @Test
    void serverOwnedFieldsAreIgnored() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        Map<String, Object> body = body("Improve it.", due(5), List.of("One"), caseVersion(brief));
        body.put("id", 999999);
        body.put("cycle", 42);
        body.put("source", "FACT_FINDING");
        body.put("status", "RESPONDED");
        body.put("requestedBy", "attacker@example.test");
        body.put("requestedAt", "2000-01-01T00:00:00Z");

        ask(brief, coordinator(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cycle").value(1))
                .andExpect(jsonPath("$.source").value("COORDINATOR"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.requestedBy").value(COORDINATOR))
                .andExpect(jsonPath("$.requestedAt").value(org.hamcrest.Matchers.not(containsString("2000-01-01"))));
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, cycle, source, status, requested_by FROM refinement_requests WHERE brief_id = ?",
                brief.getId());
        assertFalse(((Number) row.get("id")).longValue() == 999999L);
        assertEquals(Map.of("cycle", 1, "source", "COORDINATOR", "status", "OPEN", "requested_by", COORDINATOR),
                Map.of("cycle", row.get("cycle"), "source", row.get("source"), "status", row.get("status"),
                        "requested_by", row.get("requested_by")));
    }

    // ---- 13, 14: audit and notification ---------------------------------------------------

    @Test
    void auditHasOnlyStructuralDetailsAndTheEmailCarriesTheGuidanceAndDueDate() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        LocalDate due = due(12);

        ask(brief, coordinator(), body("Guidance " + MARKER, due,
                List.of("Change " + CHANGE_MARKER, "Another change"), caseVersion(brief)))
                .andExpect(status().isCreated());
        entityManager.flush();

        List<Map<String, Object>> mine = jdbcTemplate.queryForList("""
                SELECT actor, entity_type, entity_id, before_value::text AS before_value, after_value::text AS after_value
                FROM audit_log WHERE entity_id = ? AND action = 'submission.improvement_requested'
                """, brief.getId());
        assertEquals(1, mine.size());
        Map<String, Object> row = mine.getFirst();
        assertEquals(COORDINATOR, row.get("actor"));
        assertEquals("improvement", row.get("entity_type"));
        assertNull(row.get("before_value"));
        JsonNode details = objectMapper.readTree((String) row.get("after_value"));
        assertEquals(Set.of("cycle", "source", "changeCount", "dueDate", "adopted"), new HashSet<>(details.propertyNames()));
        assertEquals(1, details.get("cycle").asInt());
        assertEquals("COORDINATOR", details.get("source").asString());
        assertEquals(2, details.get("changeCount").asInt());
        assertEquals(due.toString(), details.get("dueDate").asString());
        assertFalse(details.get("adopted").asBoolean());

        // Requested-change text never reaches audit_log; guidance text is in none of the improvement rows
        // (Waluube's own submission.transitioned entry records the transition note, unchanged by this module).
        assertEquals(0, auditRowsContaining(CHANGE_MARKER, null));
        assertEquals(0, auditRowsContaining(MARKER, "submission.improvement_requested"));
        assertEquals(1, auditRowsContaining(MARKER, "submission.transitioned"));
        RequestPostProcessor admin = user("admin@example.test").roles("ADMINISTRATOR");
        String improvementFeed = body(mockMvc.perform(get("/activity").param("entityType", "improvement")
                .param("entityId", brief.getId().toString()).with(admin)).andExpect(status().isOk()));
        assertTrue(improvementFeed.contains("submission.improvement_requested"), improvementFeed);
        assertFalse(improvementFeed.contains(MARKER), improvementFeed);
        assertFalse(improvementFeed.contains(CHANGE_MARKER), improvementFeed);
        for (String feed : List.of(
                body(mockMvc.perform(get("/cases/{id}/activity", brief.getId()).with(admin))),
                body(mockMvc.perform(get("/activity").param("entityId", brief.getId().toString()).with(admin))))) {
            assertFalse(feed.contains(CHANGE_MARKER), feed);
        }

        List<Map<String, Object>> outbox = jdbcTemplate.queryForList("""
                SELECT payload->>'notificationEmail' AS recipient, payload->>'subject' AS subject,
                       payload->>'message' AS message
                FROM notification_outbox WHERE aggregate_id = ? AND event_type = 'submission.refinement_requested'
                """, brief.getId());
        assertEquals(1, outbox.size());
        assertEquals(SUBMITTER, outbox.getFirst().get("recipient"));
        String message = (String) outbox.getFirst().get("message");
        assertTrue(message.contains(due.toString()), message);
        assertTrue(message.contains("Guidance " + MARKER), message);
        assertFalse(message.contains(CHANGE_MARKER), message);
    }

    @Test
    void adoptedRequestsAreAuditedAsAdopted() throws Exception {
        Submission brief = markedForImprovementThroughTheTransitionEndpoint();
        ask(brief, officer(), body("Adopted.", due(5), List.of("One"), null)).andExpect(status().isCreated());
        entityManager.flush();
        String after = jdbcTemplate.queryForObject("""
                SELECT after_value::text FROM audit_log
                WHERE entity_id = ? AND action = 'submission.improvement_requested'
                """, String.class, brief.getId());
        JsonNode details = objectMapper.readTree(after);
        assertTrue(details.get("adopted").asBoolean());
        assertEquals("FACT_FINDING", details.get("source").asString());
    }

    @Test
    void rejectedRequestsWriteNoAuditRowAndNoNotification() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        Snapshot before = snapshot(brief);

        ask(brief, user("x@example.test").roles("REVIEWER"), body("x", due(5), List.of("x"), caseVersion(brief)))
                .andExpect(status().isForbidden());
        ask(brief, officer(), body("x", due(5), List.of("x"), caseVersion(brief))).andExpect(status().isForbidden());
        ask(brief, coordinator(), body("", due(5), List.of("x"), caseVersion(brief)))
                .andExpect(status().isUnprocessableEntity());
        ask(brief, coordinator(), body("x", due(5), List.of("x"), caseVersion(brief) + 7))
                .andExpect(status().isConflict());
        ask(UNKNOWN_BRIEF, coordinator(), body("x", due(5), List.of("x"), 0L)).andExpect(status().isNotFound());

        assertEquals(before, snapshot(brief));
    }

    // ---- 22: what the submitter sees ------------------------------------------------------

    @Test
    void theSubmittersPagesNeverShowTheRequestedChangeTexts() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        ask(brief, coordinator(), body("Guidance " + MARKER, due(5), List.of("Change " + CHANGE_MARKER),
                caseVersion(brief))).andExpect(status().isCreated());

        RequestPostProcessor submitter = user(SUBMITTER).roles("SUBMITTER");
        for (String page : List.of(
                body(mockMvc.perform(get("/cases/{id}", brief.getId()).with(submitter)).andExpect(status().isOk())),
                body(mockMvc.perform(get("/cases/{id}/activity", brief.getId()).with(submitter))
                        .andExpect(status().isOk())))) {
            assertFalse(page.contains(CHANGE_MARKER), page);
            assertFalse(page.contains("submission.improvement_requested"), page);
        }
    }

    // ---- 23: contract ---------------------------------------------------------------------

    @Test
    void responseFieldNamesMatchTheRecord() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        JsonNode response = objectMapper.readTree(body(ask(brief, coordinator(),
                body("Improve it.", due(5), List.of("One"), caseVersion(brief))).andExpect(status().isCreated())));

        assertEquals(Set.of("id", "briefId", "cycle", "source", "guidanceNote", "dueDate", "status", "requestedBy",
                "requestedAt", "respondedAt", "requestedChanges", "version", "briefStage"),
                new HashSet<>(response.propertyNames()));
        assertEquals(Set.of("position", "text"), new HashSet<>(response.get("requestedChanges").get(0).propertyNames()));
    }

    // ---- history (17, 18, 20, 21, 23) -----------------------------------------------------

    @Test
    void historyIsEmptyWithoutRequests() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        mockMvc.perform(get(HISTORY, brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.requests").isEmpty());
    }

    @Test
    void historyListsCyclesInOrderWithTheirChangesAndRevisions() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        ask(brief, coordinator(), body("Round one.", due(5), List.of("A1", "A2"), caseVersion(brief)))
                .andExpect(status().isCreated());
        RefinementRequest first = requests.findTopByBriefIdOrderByCycleDesc(brief.getId()).orElseThrow();
        first.setStatus(RefinementStatus.RESPONDED);
        first.setRespondedAt(Instant.parse("2026-11-20T10:00:00Z"));
        requests.saveAndFlush(first);
        revisions.saveAndFlush(new CaseRevision(brief.getId(), 2, first.getId(), "Added the budget.", SUBMITTER,
                Instant.parse("2026-11-20T10:00:00Z")));
        Submission reloaded = submissions.findById(brief.getId()).orElseThrow();
        reloaded.transitionTo(SubmissionStage.RESUBMITTED, Instant.parse("2026-11-20T10:00:00Z"));
        reloaded.transitionTo(SubmissionStage.IN_REVIEW, Instant.parse("2026-11-20T11:00:00Z"));
        submissions.saveAndFlush(reloaded);
        ask(brief, coordinator(), body("Round two.", due(6), List.of("B1", "B2", "B3"), caseVersion(brief)))
                .andExpect(status().isCreated());

        for (RequestPostProcessor reader : List.of(officer(), coordinator(), user("admin@example.test").roles("ADMINISTRATOR"))) {
            mockMvc.perform(get(HISTORY, brief.getId()).with(reader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requests", hasSize(2)))
                    .andExpect(jsonPath("$.requests[0].cycle").value(1))
                    .andExpect(jsonPath("$.requests[0].guidanceNote").value("Round one."))
                    .andExpect(jsonPath("$.requests[0].status").value("RESPONDED"))
                    .andExpect(jsonPath("$.requests[0].respondedAt").value("2026-11-20T10:00:00Z"))
                    .andExpect(jsonPath("$.requests[0].requestedChanges[0].text").value("A1"))
                    .andExpect(jsonPath("$.requests[0].requestedChanges[1].text").value("A2"))
                    .andExpect(jsonPath("$.requests[0].revision.versionNumber").value(2))
                    .andExpect(jsonPath("$.requests[0].revision.whatChanged").value("Added the budget."))
                    .andExpect(jsonPath("$.requests[0].revision.submittedBy").value(SUBMITTER))
                    .andExpect(jsonPath("$.requests[0].revision.submittedAt").value("2026-11-20T10:00:00Z"))
                    .andExpect(jsonPath("$.requests[1].cycle").value(2))
                    .andExpect(jsonPath("$.requests[1].status").value("OPEN"))
                    .andExpect(jsonPath("$.requests[1].requestedChanges", hasSize(3)))
                    .andExpect(jsonPath("$.requests[1].requestedChanges[2].position").value(3))
                    .andExpect(jsonPath("$.requests[1].requestedChanges[2].text").value("B3"))
                    .andExpect(jsonPath("$.requests[1].revision").value(nullValue()));
        }

        JsonNode response = objectMapper.readTree(body(mockMvc.perform(get(HISTORY, brief.getId()).with(coordinator()))));
        assertEquals(Set.of("briefId", "requests"), new HashSet<>(response.propertyNames()));
        assertEquals(Set.of("id", "cycle", "source", "guidanceNote", "dueDate", "status", "requestedBy", "requestedAt",
                "respondedAt", "requestedChanges", "revision"), new HashSet<>(response.get("requests").get(0).propertyNames()));
        assertEquals(Set.of("position", "text"),
                new HashSet<>(response.get("requests").get(0).get("requestedChanges").get(0).propertyNames()));
        assertEquals(Set.of("versionNumber", "whatChanged", "submittedBy", "submittedAt"),
                new HashSet<>(response.get("requests").get(0).get("revision").propertyNames()));
    }

    @Test
    void historyNeverWrites() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        ask(brief, coordinator(), body("Improve it.", due(5), List.of("One", "Two"), caseVersion(brief)))
                .andExpect(status().isCreated());
        Map<String, Object> before = globalCounts();

        mockMvc.perform(get(HISTORY, brief.getId()).with(coordinator())).andExpect(status().isOk());
        mockMvc.perform(get(HISTORY, brief.getId()).with(officer())).andExpect(status().isOk());
        mockMvc.perform(get(HISTORY, UNKNOWN_BRIEF).with(coordinator())).andExpect(status().isNotFound());

        assertEquals(before, globalCounts());
    }

    // ---- helpers --------------------------------------------------------------------------

    record Snapshot(String stage, int history, int refinements, int changes, int auditRows, int outboxRows) {
    }

    private Snapshot snapshot(Submission brief) {
        entityManager.flush();
        return new Snapshot(
                stage(brief),
                historyRows(brief),
                refinementRows(brief),
                jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM refinement_requested_changes c
                        JOIN refinement_requests r ON r.id = c.request_id WHERE r.brief_id = ?
                        """, Integer.class, brief.getId()),
                jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ?", Integer.class,
                        brief.getId()),
                jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox WHERE aggregate_id = ?",
                        Integer.class, brief.getId()));
    }

    private Map<String, Object> globalCounts() {
        entityManager.flush();
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT count(*) FROM refinement_requests) AS requests,
                       (SELECT count(*) FROM refinement_requested_changes) AS changes,
                       (SELECT count(*) FROM case_revisions) AS revisions,
                       (SELECT count(*) FROM audit_log) AS audit,
                       (SELECT count(*) FROM notification_outbox) AS outbox,
                       (SELECT coalesce(sum(version), 0) FROM refinement_requests) AS versions
                """);
    }

    private int auditRowsContaining(String marker, String action) {
        entityManager.flush();
        String sql = """
                SELECT count(*) FROM audit_log
                WHERE concat_ws('|', actor, actor_role, action, entity_type, entity_id::text,
                                before_value::text, after_value::text, department_id::text, request_id) LIKE ?
                """ + (action == null ? "" : " AND action = ?");
        return action == null
                ? jdbcTemplate.queryForObject(sql, Integer.class, "%" + marker + "%")
                : jdbcTemplate.queryForObject(sql, Integer.class, "%" + marker + "%", action);
    }

    private String stage(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT stage FROM submissions WHERE id = ?", String.class, brief.getId());
    }

    private int historyRows(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM case_stage_history WHERE submission_id = ?",
                Integer.class, brief.getId());
    }

    private int refinementRows(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM refinement_requests WHERE brief_id = ?",
                Integer.class, brief.getId());
    }

    /** The case version exactly as a client reads it: GET /cases/{id} at $.submission.version. */
    private long caseVersion(Submission brief) throws Exception {
        entityManager.flush();
        return objectMapper.readTree(body(mockMvc.perform(get("/cases/{id}", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()))).get("submission").get("version").asLong();
    }

    /** Puts a brief into IMPROVEMENT_REQUESTED through Waluube's real transition endpoint (no refinement row). */
    private Submission markedForImprovementThroughTheTransitionEndpoint() throws Exception {
        Submission brief = briefInStage(SubmissionStage.IN_REVIEW);
        mockMvc.perform(post("/cases/{id}/transitions", brief.getId()).with(coordinator())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "to_stage", "IMPROVEMENT_REQUESTED",
                                "note", "Set up by the test.",
                                "version", caseVersion(brief)))))
                .andExpect(status().isOk());
        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
        return brief;
    }

    private ResultActions ask(Submission brief, RequestPostProcessor caller, Map<String, Object> body) throws Exception {
        return ask(brief.getId(), caller, body);
    }

    private ResultActions ask(UUID briefId, RequestPostProcessor caller, Map<String, Object> body) throws Exception {
        return mockMvc.perform(withJson(post(ASK, briefId), body).with(caller));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withJson(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Map<String, Object> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    static Map<String, Object> body(String guidance, LocalDate dueDate, List<String> changes, Long caseVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guidanceNote", guidance);
        body.put("dueDate", dueDate == null ? null : dueDate.toString());
        body.put("requestedChanges", changes);
        body.put("caseVersion", caseVersion);
        return body;
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    static LocalDate today() {
        return LocalDate.now(HUB_ZONE);
    }

    static LocalDate due(int days) {
        return today().plusDays(days);
    }

    static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }

    static RequestPostProcessor officer() {
        return user(OFFICER).roles("FACT_FINDING_OFFICER");
    }

    private Submission briefInStage(SubmissionStage target) {
        return submissions.saveAndFlush(ImprovementTestBriefs.inStage(target, "CASE-IM-"));
    }
}
