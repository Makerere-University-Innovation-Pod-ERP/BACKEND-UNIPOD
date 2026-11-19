package org.unipod.unipoderpbackend.controller.improvement;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewFollowUpNote;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewFollowUpNoteRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyImprovementApiIntegrationTest {
    private static final String MINE = "/cases/{id}/my-improvement";
    private static final String ANSWER = "/cases/{id}/improved-version";
    private static final String OWNER = "sub@example.test";
    private static final String OTHER_SUBMITTER = "other-sub@example.test";
    private static final String COORDINATOR = "marker-coord@example.test";
    private static final String OFFICER = "marker-officer@example.test";
    private static final String MARKER = "SECRET-MARKER-xyz";
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ReviewSettingsRepository reviewSettings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private ReviewFollowUpNoteRepository followUpNotes;

    @Autowired
    private FactFindingRepository factFinding;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- GET my-improvement (1-4) --------------------------------------------------------

    @Test
    void openRequestIsShownWithGuidanceDueDateItemsAndVersion() throws Exception {
        Submission brief = askedBrief("Add a costed budget.", List.of("Budget table", "Two letters of support"));

        mine(brief)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.briefStage").value("IMPROVEMENT_REQUESTED"))
                .andExpect(jsonPath("$.openRequest.id").value(notNullValue()))
                .andExpect(jsonPath("$.openRequest.cycle").value(1))
                .andExpect(jsonPath("$.openRequest.guidanceNote").value("Add a costed budget."))
                .andExpect(jsonPath("$.openRequest.dueDate").value(due(10).toString()))
                .andExpect(jsonPath("$.openRequest.requestedChanges", hasSize(2)))
                .andExpect(jsonPath("$.openRequest.requestedChanges[0].position").value(1))
                .andExpect(jsonPath("$.openRequest.requestedChanges[0].text").value("Budget table"))
                .andExpect(jsonPath("$.openRequest.requestedChanges[1].text").value("Two letters of support"))
                .andExpect(jsonPath("$.openRequest.version").value(0))
                .andExpect(jsonPath("$.previousRequests").isEmpty());
    }

    @Test
    void afterAnsweringThePreviousRequestShowsTheRevision() throws Exception {
        Submission brief = askedBrief("Add a costed budget.", List.of("Budget table"));
        answer(brief, answerBody("I added a budget table.", 0L)).andExpect(status().isCreated());

        mine(brief)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefStage").value("RESUBMITTED"))
                .andExpect(jsonPath("$.openRequest").value(nullValue()))
                .andExpect(jsonPath("$.previousRequests", hasSize(1)))
                .andExpect(jsonPath("$.previousRequests[0].cycle").value(1))
                .andExpect(jsonPath("$.previousRequests[0].guidanceNote").value("Add a costed budget."))
                .andExpect(jsonPath("$.previousRequests[0].requestedChanges[0].text").value("Budget table"))
                .andExpect(jsonPath("$.previousRequests[0].respondedAt").value(notNullValue()))
                .andExpect(jsonPath("$.previousRequests[0].revision.versionNumber").value(2))
                .andExpect(jsonPath("$.previousRequests[0].revision.whatChanged").value("I added a budget table."))
                .andExpect(jsonPath("$.previousRequests[0].revision.submittedAt").value(notNullValue()));
    }

    @Test
    void answeredAndOpenCyclesAreSplit() throws Exception {
        Submission brief = askedBrief("Round one.", List.of("A1"));
        answer(brief, answerBody("Done one.", 0L)).andExpect(status().isCreated());
        backToReviewAndAskAgain(brief, "Round two.", List.of("B1", "B2"));

        mine(brief)
                .andExpect(jsonPath("$.previousRequests", hasSize(1)))
                .andExpect(jsonPath("$.previousRequests[0].cycle").value(1))
                .andExpect(jsonPath("$.openRequest.cycle").value(2))
                .andExpect(jsonPath("$.openRequest.guidanceNote").value("Round two."))
                .andExpect(jsonPath("$.openRequest.requestedChanges", hasSize(2)));
    }

    @Test
    void briefWithoutRequestsShowsNothingOpen() throws Exception {
        Submission brief = ownedBrief(SubmissionStage.IN_REVIEW);

        mine(brief)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefStage").value("IN_REVIEW"))
                .andExpect(jsonPath("$.openRequest").value(nullValue()))
                .andExpect(jsonPath("$.previousRequests").isEmpty());
    }

    // ---- 5: nothing staff-internal reaches the submitter ---------------------------------

    @Test
    void theSubmittersViewCarriesNoStaffIdentityAndNothingFromReviewsOrFactFinding() throws Exception {
        Submission brief = ownedBrief(SubmissionStage.IN_REVIEW);
        FactFindingCheck check = new FactFindingCheck(brief.getId(), 1, OFFICER);
        check.setTitleNote("Fact-finding note " + MARKER);
        check.setMissingItems("Missing " + MARKER);
        factFinding.saveAndFlush(check);
        reviewSettings.saveAndFlush(new ReviewSettings(brief.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        ReviewAssignment assignment = new ReviewAssignment(brief.getId(), 1, "marker-reviewer@example.test", null, 1,
                COORDINATOR);
        assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
        assignment = assignments.saveAndFlush(assignment);
        Review review = reviews.saveAndFlush(new Review(assignment.getId(), ReviewDecision.CHANGES_NEEDED,
                "Reviewer comment " + MARKER, Instant.parse("2026-11-12T10:00:00Z")));
        followUpNotes.saveAndFlush(new ReviewFollowUpNote(review.getId(), "Follow-up " + MARKER,
                "marker-reviewer@example.test"));
        askAs(brief, user(COORDINATOR).roles("INNOVATION_COORDINATOR"), "Guidance for you.", List.of("Item one"));
        answer(brief, answerBody("My answer.", 0L)).andExpect(status().isCreated());
        backToReviewAndAskAgain(brief, "Second guidance.", List.of("Item two"));

        String body = body(mine(brief).andExpect(status().isOk()));
        for (String forbidden : List.of(MARKER, COORDINATOR, OFFICER, "marker-reviewer@example.test",
                "requestedBy", "\"source\"", "COORDINATOR", "FACT_FINDING", "CHANGES_NEEDED", "RECOMMENDED",
                "comment", "decision")) {
            assertFalse(body.contains(forbidden), forbidden + " leaked: " + body);
        }
    }

    // ---- 6: access ------------------------------------------------------------------------

    @Test
    void anotherSubmittersBriefLooksExactlyLikeAnUnknownBrief() throws Exception {
        Submission brief = askedBrief("Guidance.", List.of("One"));

        JsonNode unknownGet = json(body(mine(UNKNOWN_BRIEF, submitter(OWNER)).andExpect(status().isNotFound())));
        JsonNode otherGet = json(body(mine(brief.getId(), submitter(OTHER_SUBMITTER)).andExpect(status().isNotFound())));
        assertEquals(withoutInstance(unknownGet), withoutInstance(otherGet));
        assertEquals("Submission not found", otherGet.get("title").asString());
        assertEquals("The requested submission was not found.", otherGet.get("detail").asString());
    }



    // ---- 7: read-only --------------------------------------------------------------------

    @Test
    void myImprovementNeverWrites() throws Exception {
        Submission brief = askedBrief("Guidance.", List.of("One", "Two"));
        Map<String, Object> before = globalCounts();

        mine(brief).andExpect(status().isOk());
        mine(brief.getId(), submitter(OTHER_SUBMITTER)).andExpect(status().isNotFound());

        assertEquals(before, globalCounts());
    }

    // ---- POST improved-version (8-10) ----------------------------------------------------




    // ---- 11-17: rejections ---------------------------------------------------------------








    // ---- 18: audit -----------------------------------------------------------------------



    // ---- 21, 22: staff history and contract ----------------------------------------------



    // ---- helpers -------------------------------------------------------------------------

    record Snapshot(String stage, int briefVersion, int history, int revisions, String requestStatus, int audit,
                    int outbox) {
    }

    private Snapshot snapshot(Submission brief) {
        entityManager.flush();
        List<String> status = jdbcTemplate.queryForList(
                "SELECT status FROM refinement_requests WHERE brief_id = ? ORDER BY cycle DESC LIMIT 1",
                String.class, brief.getId());
        return new Snapshot(stage(brief), briefVersion(brief), historyRows(brief), revisions(brief),
                status.isEmpty() ? null : status.getFirst(),
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
                       (SELECT count(*) FROM submissions) AS submissions,
                       (SELECT coalesce(sum(version), 0) FROM submissions) AS submission_versions
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

    private int briefVersion(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT brief_version FROM submissions WHERE id = ?", Integer.class,
                brief.getId());
    }

    private int historyRows(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM case_stage_history WHERE submission_id = ?",
                Integer.class, brief.getId());
    }

    private int revisions(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM case_revisions WHERE brief_id = ?", Integer.class,
                brief.getId());
    }

    private long caseVersion(Submission brief) throws Exception {
        entityManager.flush();
        return json(body(mockMvc.perform(get("/cases/{id}", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()))).get("submission").get("version").asLong();
    }

    private Submission ownedBrief(SubmissionStage stage) {
        return submissions.saveAndFlush(ImprovementTestBriefs.ownedBy(OWNER, stage, "CASE-IV-"));
    }

    /** An IN_REVIEW brief owned by OWNER on which the coordinator asked for changes through the real endpoint. */
    private Submission askedBrief(String guidance, List<String> items) throws Exception {
        Submission brief = ownedBrief(SubmissionStage.IN_REVIEW);
        askAs(brief, coordinator(), guidance, items);
        return brief;
    }

    private void askAs(Submission brief, RequestPostProcessor staff, String guidance, List<String> items)
            throws Exception {
        mockMvc.perform(json(post("/cases/{id}/refinement-requests", brief.getId()),
                        ImprovementTestBodies.body(guidance, due(10), items, caseVersion(brief)))
                        .with(staff))
                .andExpect(status().isCreated());
    }

    /** Test setup: the brief returns to review (model transitions), then the coordinator asks again. */
    private void backToReviewAndAskAgain(Submission brief, String guidance, List<String> items) throws Exception {
        entityManager.flush();
        Submission reloaded = submissions.findById(brief.getId()).orElseThrow();
        reloaded.transitionTo(SubmissionStage.IN_REVIEW, Instant.parse("2026-11-20T11:00:00Z"));
        submissions.saveAndFlush(reloaded);
        askAs(brief, coordinator(), guidance, items);
    }

    private ResultActions mine(Submission brief) throws Exception {
        return mine(brief.getId(), submitter(OWNER));
    }

    private ResultActions mine(UUID briefId, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(get(MINE, briefId).with(caller));
    }

    private ResultActions answer(Submission brief, Map<String, Object> body) throws Exception {
        return mockMvc.perform(json(post(ANSWER, brief.getId()), body).with(submitter(OWNER)));
    }

    static Map<String, Object> answerBody(String whatChanged, Long requestVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("whatChanged", whatChanged);
        body.put("requestVersion", requestVersion);
        return body;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private JsonNode withoutInstance(JsonNode problem) {
        tools.jackson.databind.node.ObjectNode copy = (tools.jackson.databind.node.ObjectNode) problem.deepCopy();
        copy.remove("instance");
        return copy;
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    private static LocalDate due(int days) {
        return LocalDate.now(HUB_ZONE).plusDays(days);
    }

    private static RequestPostProcessor submitter(String email) {
        return user(email).roles("SUBMITTER");
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }
}
