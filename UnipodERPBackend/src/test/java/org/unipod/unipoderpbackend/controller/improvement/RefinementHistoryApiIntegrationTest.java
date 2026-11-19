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
class RefinementHistoryApiIntegrationTest {
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





    // ---- 4: officer limits ----------------------------------------------------------------



    // ---- 5, 6: adopting a brief already marked for improvement ----------------------------



    // ---- 7: a second cycle ----------------------------------------------------------------


    // ---- 8: case version ------------------------------------------------------------------



    // ---- 9: validation --------------------------------------------------------------------



    // ---- 10: stages that are not allowed --------------------------------------------------


    // ---- 11: security and request shape --------------------------------------------------




    // ---- 13, 14: audit and notification ---------------------------------------------------




    // ---- 22: what the submitter sees ------------------------------------------------------


    // ---- 23: contract ---------------------------------------------------------------------


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
        assertEquals(Set.of("versionNumber", "whatChanged", "submittedBy", "submittedAt", "id"),
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
        // Main's RefinementRequestAskRequest: the case version goes in "version".
        body.put("version", caseVersion);
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
