package org.unipod.unipoderpbackend.controller.review;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewRoundApiIntegrationTest {
    private static final String ROUNDS = "/cases/{id}/review-rounds";
    private static final String COORDINATOR = "coord@example.test";
    private static final String OWNER = "round-owner@example.test";
    private static final String REVIEWER_ONE = "reviewer-one@example.test";
    private static final String REVIEWER_TWO = "reviewer-two@example.test";
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private FactFindingRepository factFinding;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- 1-3: starting round 2 -------------------------------------------------------------

    @Test
    void coordinatorStartsRoundTwoAfterAFullImprovementCycle() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        String due = today().plusDays(10).toString();

        start(brief, coordinator(), body("SEQUENTIAL", 2, due, settingsVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.mode").value("SEQUENTIAL"))
                .andExpect(jsonPath("$.reviewsNeeded").value(2))
                .andExpect(jsonPath("$.dueDate").value(due))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.reviewers").isEmpty())
                .andExpect(jsonPath("$.quorum.required").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(0))
                .andExpect(jsonPath("$.quorum.met").value(false));

        entityManager.flush();
        List<Map<String, Object>> audit = jdbcTemplate.queryForList("""
                SELECT actor, entity_type, after_value::text AS after_value FROM audit_log
                WHERE entity_id = ? AND action = 'submission.review_round_started'
                """, brief.getId());
        assertEquals(1, audit.size());
        assertEquals(COORDINATOR, audit.getFirst().get("actor"));
        assertEquals("review", audit.getFirst().get("entity_type"));
        JsonNode details = json((String) audit.getFirst().get("after_value"));
        assertEquals(Set.of("round", "mode", "reviewsNeeded", "dueDate"), new HashSet<>(details.propertyNames()));
        assertEquals(2, details.get("round").asInt());
        assertEquals("SEQUENTIAL", details.get("mode").asString());
        assertEquals(2, details.get("reviewsNeeded").asInt());
        assertEquals(due, details.get("dueDate").asString());
        assertFalse(audit.getFirst().get("after_value").toString().contains("@example.test"));
    }

    @Test
    void roundOneIsUntouchedAndStillReadable() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        Map<String, Object> roundOneBefore = roundOne(brief);

        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief))).andExpect(status().isCreated());

        assertEquals(roundOneBefore, roundOne(brief));
        mockMvc.perform(get("/cases/{id}/reviews", brief.getId()).param("round", "1").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.latestRound").value(2))
                .andExpect(jsonPath("$.reviews", hasSize(1)))
                .andExpect(jsonPath("$.reviews[0].reviewerId").value(REVIEWER_ONE))
                .andExpect(jsonPath("$.reviews[0].comment").value("Round one comment."))
                .andExpect(jsonPath("$.reviews[0].followUpNotes[0].note").value("Round one note."));
        mockMvc.perform(get("/cases/{id}/reviews", brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.reviews").isEmpty())
                .andExpect(jsonPath("$.notSubmittedCount").value(0));
    }

    @Test
    void quorumAndPanelDescribeRoundTwoAndTheOutcomeIsBlocked() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        mockMvc.perform(get("/cases/{id}/review-quorum", brief.getId()).with(coordinator()))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.met").value(true));

        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief))).andExpect(status().isCreated());

        mockMvc.perform(get("/cases/{id}/review-quorum", brief.getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.required").value(1))
                .andExpect(jsonPath("$.completed").value(0))
                .andExpect(jsonPath("$.met").value(false));
        mockMvc.perform(get("/cases/{id}/reviewers", brief.getId()).with(coordinator()))
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.reviewers").isEmpty())
                .andExpect(jsonPath("$.quorum.met").value(false));
        outcome(brief)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Save stays disabled until the configured reviewer quorum is met"));
    }

    // ---- 4-6: reviewers in round 2 --------------------------------------------------------

    @Test
    void reviewersFollowRoundTwoOnlyAndRoundOneSubmissionsAreUnaffected() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief))).andExpect(status().isCreated());

        // Not yet in round 2: the queue (latest round only) has no row for the brief.
        mockMvc.perform(get("/queues/reviews").with(reviewer(REVIEWER_ONE)))
                .andExpect(jsonPath("$.assignedToMe").value(0))
                .andExpect(jsonPath("$.rows.items").isEmpty());

        // The same person can be assigned again in round 2.
        assign(brief, REVIEWER_ONE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.reviewers[0].reviewerId").value(REVIEWER_ONE))
                .andExpect(jsonPath("$.reviewers[0].status").value("NOT_STARTED"));
        mockMvc.perform(get("/queues/reviews").with(reviewer(REVIEWER_ONE)))
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].round").value(2))
                .andExpect(jsonPath("$.rows.items[0].position").value(1))
                .andExpect(jsonPath("$.rows.items[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(true));

        Map<String, Object> roundOneBefore = roundOne(brief);
        submitReview(brief, REVIEWER_ONE, "RECOMMENDED", "Round two comment.").andExpect(status().isCreated())
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.quorum.completed").value(1))
                .andExpect(jsonPath("$.quorum.met").value(true));
        assertEquals(roundOneBefore, roundOne(brief));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ? AND a.reviewer_id = ?
                """, Integer.class, brief.getId(), REVIEWER_ONE));
        mockMvc.perform(get("/cases/{id}/review-quorum", brief.getId()).with(coordinator()))
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.met").value(true));
        outcome(brief).andExpect(status().isOk());
    }

    // ---- 7-12: refusals --------------------------------------------------------------------

    @Test
    void roundOneNotFinishedIsAConflict() throws Exception {
        Submission brief = briefAfterImprovementCycle(2);
        Snapshot before = snapshot(brief);

        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The current review round is still open."));
        assertEquals(before, snapshot(brief));
    }

    @Test
    void briefThatNeverCameBackFromImprovementIsAConflict() throws Exception {
        Submission brief = briefWithFinishedRoundOne();
        Snapshot before = snapshot(brief);

        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("A new review round can only start after an improved version has been accepted."));
        assertEquals(before, snapshot(brief));
    }

    @Test
    void briefNotInReviewOrWithoutSettingsIsAConflict() throws Exception {
        Submission resubmitted = briefWithFinishedRoundOne();
        askAndAnswer(resubmitted);
        assertEquals("RESUBMITTED", stage(resubmitted));
        start(resubmitted, coordinator(), body("PARALLEL", 1, null, settingsVersion(resubmitted)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A new review round can only start while the brief is in review."));

        Submission improvementRequested = briefWithFinishedRoundOne();
        ask(improvementRequested);
        start(improvementRequested, coordinator(), body("PARALLEL", 1, null, settingsVersion(improvementRequested)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A new review round can only start while the brief is in review."));

        Submission noSettings = brief(SubmissionStage.IN_REVIEW);
        start(noSettings, coordinator(), body("PARALLEL", 1, null, 0L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("There is no review round to follow. Set the review settings first."));
        assertEquals(0, roundRows(noSettings));
    }

    @Test
    void missingOrStaleSettingsVersionIsRejected() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        long version = settingsVersion(brief);
        Snapshot before = snapshot(brief);

        start(brief, coordinator(), body("PARALLEL", 1, null, null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("settingsVersion")));
        start(brief, coordinator(), body("PARALLEL", 1, null, version + 4))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The review settings were changed by another user. Reload and try again."));
        assertEquals(before, snapshot(brief));
    }

    @Test
    void fieldsAreValidated() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        long version = settingsVersion(brief);
        Snapshot before = snapshot(brief);

        Map<Map<String, Object>, String> cases = new LinkedHashMap<>();
        cases.put(body("PARALLEL", 0, null, version), "reviewsNeeded");
        cases.put(body("PARALLEL", 21, null, version), "reviewsNeeded");
        cases.put(body("PARALLEL", null, null, version), "reviewsNeeded");
        cases.put(body(null, 1, null, version), "mode");
        cases.put(body("PARALLEL", 1, today().minusDays(1).toString(), version), "dueDate");
        for (Map.Entry<Map<String, Object>, String> entry : cases.entrySet()) {
            start(brief, coordinator(), entry.getKey())
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(entry.getValue())));
        }
        assertEquals(before, snapshot(brief));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1|", "20|", "1|today"})
    void limitsAndOptionalDueDateAreAccepted(String spec) throws Exception {
        Submission brief = briefAfterImprovementCycle();
        String[] parts = spec.split("\\|", -1);
        String due = parts[1].isEmpty() ? null : today().toString();

        start(brief, coordinator(), body("PARALLEL", Integer.parseInt(parts[0]), due, settingsVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewsNeeded").value(Integer.parseInt(parts[0])))
                .andExpect(due == null ? jsonPath("$.dueDate").value(nullValue()) : jsonPath("$.dueDate").value(due));
    }

    @Test
    void aSecondStartRightAfterwardsIsAConflict() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief))).andExpect(status().isCreated());
        Snapshot before = snapshot(brief);

        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The current review round is still open."));
        assertEquals(before, snapshot(brief));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM review_settings WHERE brief_id = ? AND round = 2", Integer.class, brief.getId()));
    }

    @Test
    void finishedRoundTwoWithoutANewerImprovementIsAConflict() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief))).andExpect(status().isCreated());
        assign(brief, REVIEWER_TWO).andExpect(status().isCreated());
        submitReview(brief, REVIEWER_TWO, "RECOMMENDED", "Fine.").andExpect(status().isCreated());

        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("A new review round can only start after an improved version has been accepted."));
    }

    // ---- 14: a second improvement cycle ---------------------------------------------------

    @Test
    void aSecondImprovementCycleAllowsRoundThreeAndLeavesRoundTwoUntouched() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief))).andExpect(status().isCreated());
        assign(brief, REVIEWER_TWO).andExpect(status().isCreated());
        submitReview(brief, REVIEWER_TWO, "CHANGES_NEEDED", "Still missing data.").andExpect(status().isCreated());
        Map<String, Object> roundTwoBefore = round(brief, 2);

        askAnswerAccept(brief);
        start(brief, coordinator(), body("SEQUENTIAL", 3, null, settingsVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.round").value(3))
                .andExpect(jsonPath("$.reviewsNeeded").value(3));

        assertEquals(roundTwoBefore, round(brief, 2));
        mockMvc.perform(get("/cases/{id}/reviews", brief.getId()).param("round", "2").with(coordinator()))
                .andExpect(jsonPath("$.reviews[0].comment").value("Still missing data."))
                .andExpect(jsonPath("$.latestRound").value(3));
    }

    // ---- 15-17: security, body, audit -----------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"REVIEWER", "FACT_FINDING_OFFICER", "SUBMITTER", "RECEPTIONIST", "SECTOR_OFFICER",
            "IP_LIAISON", "OBSERVER"})
    void otherRolesAreForbidden(String role) throws Exception {
        Submission brief = briefAfterImprovementCycle();
        Snapshot before = snapshot(brief);

        start(brief, user(OWNER).roles(role), body("PARALLEL", 1, null, settingsVersion(brief)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Forbidden"));
        assertEquals(before, snapshot(brief));
    }

    @Test
    void administratorCanStartARound() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        start(brief, user("admin@example.test").roles("ADMINISTRATOR"), body("PARALLEL", 1, null, settingsVersion(brief)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.round").value(2));
    }

    @Test
    void unauthenticatedUnknownAndMalformedRequests() throws Exception {
        mockMvc.perform(json(post(ROUNDS, UNKNOWN_BRIEF), body("PARALLEL", 1, null, 0L)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post(ROUNDS, UNKNOWN_BRIEF), body("PARALLEL", 1, null, 0L)).with(coordinator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Submission not found"));
        mockMvc.perform(json(post("/cases/not-a-uuid/review-rounds"), body("PARALLEL", 1, null, 0L)).with(coordinator()))
                .andExpect(status().isBadRequest());
        Submission brief = briefAfterImprovementCycle();
        mockMvc.perform(post(ROUNDS, brief.getId()).with(coordinator())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
        assertEquals(1, roundRows(brief));
    }

    @Test
    void serverOwnedFieldsAreIgnored() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        Map<String, Object> body = body("PARALLEL", 1, null, settingsVersion(brief));
        body.put("round", 9);
        body.put("setBy", "attacker@example.test");

        start(brief, coordinator(), body).andExpect(status().isCreated()).andExpect(jsonPath("$.round").value(2));
        assertEquals(COORDINATOR, jdbcTemplate.queryForObject(
                "SELECT set_by FROM review_settings WHERE brief_id = ? AND round = 2", String.class, brief.getId()));
    }

    @Test
    void rejectedRequestsWriteNoAuditRow() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        Snapshot before = snapshot(brief);

        start(brief, reviewer(REVIEWER_ONE), body("PARALLEL", 1, null, settingsVersion(brief)))
                .andExpect(status().isForbidden());
        start(brief, coordinator(), body("PARALLEL", 1, null, null)).andExpect(status().isUnprocessableEntity());
        start(brief, coordinator(), body("PARALLEL", 0, null, settingsVersion(brief)))
                .andExpect(status().isUnprocessableEntity());
        start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief) + 2)).andExpect(status().isConflict());

        assertEquals(before, snapshot(brief));
    }

    // ---- 20: contract ---------------------------------------------------------------------

    @Test
    void responseFieldNamesMatchThePanelRecord() throws Exception {
        Submission brief = briefAfterImprovementCycle();
        JsonNode response = json(body(start(brief, coordinator(), body("PARALLEL", 1, null, settingsVersion(brief)))
                .andExpect(status().isCreated())));
        assertEquals(Set.of("briefId", "configured", "round", "mode", "reviewsNeeded", "dueDate", "version", "quorum",
                "reviewers"), new HashSet<>(response.propertyNames()));
        assertEquals(Set.of("required", "completed", "met"), new HashSet<>(response.get("quorum").propertyNames()));
    }

    // ---- helpers -------------------------------------------------------------------------

    record Snapshot(int rounds, int auditRows, String stage) {
    }

    private Snapshot snapshot(Submission brief) {
        return new Snapshot(roundRows(brief),
                jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ? "
                        + "AND action = 'submission.review_round_started'", Integer.class, brief.getId()),
                stage(brief));
    }

    /** Everything stored for round 1: settings, assignments, reviews and follow-up notes. */
    private Map<String, Object> roundOne(Submission brief) {
        return round(brief, 1);
    }

    private Map<String, Object> round(Submission brief, int round) {
        entityManager.flush();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("settings", jdbcTemplate.queryForList(
                "SELECT * FROM review_settings WHERE brief_id = ? AND round = ?", brief.getId(), round));
        state.put("assignments", jdbcTemplate.queryForList(
                "SELECT * FROM review_assignments WHERE brief_id = ? AND round = ? ORDER BY id", brief.getId(), round));
        state.put("reviews", jdbcTemplate.queryForList("""
                SELECT r.* FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ? AND a.round = ? ORDER BY r.id
                """, brief.getId(), round));
        state.put("notes", jdbcTemplate.queryForList("""
                SELECT n.* FROM review_follow_up_notes n JOIN reviews r ON r.id = n.review_id
                JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ? AND a.round = ? ORDER BY n.id
                """, brief.getId(), round));
        return state;
    }

    private Submission briefAfterImprovementCycle() throws Exception {
        return briefAfterImprovementCycle(1);
    }

    /**
     * Round 1 with reviewsNeeded {@code needed} and REVIEWER_ONE's review and follow-up note, then a full
     * improvement cycle through the real endpoints: ask for changes, improved version, accept.
     */
    private Submission briefAfterImprovementCycle(int needed) throws Exception {
        Submission brief = briefWithRoundOne(needed);
        askAnswerAccept(brief);
        assertEquals("IN_REVIEW", stage(brief));
        return brief;
    }

    private Submission briefWithFinishedRoundOne() throws Exception {
        return briefWithRoundOne(1);
    }

    private Submission briefWithRoundOne(int needed) throws Exception {
        Submission brief = brief(SubmissionStage.IN_REVIEW);
        for (String email : List.of(REVIEWER_ONE, REVIEWER_TWO)) {
            if (users.findByEmailIgnoreCase(email).isEmpty()) {
                users.saveAndFlush(new ApiUser(UUID.randomUUID(), email, "not-a-real-hash", ApiUserRole.REVIEWER,
                        Instant.parse("2026-11-01T08:00:00Z")));
            }
        }
        mockMvc.perform(json(put("/cases/{id}/review-settings", brief.getId()),
                        Map.of("mode", "PARALLEL", "reviewsNeeded", needed)).with(coordinator()))
                .andExpect(status().isOk());
        assign(brief, REVIEWER_ONE).andExpect(status().isCreated());
        submitReview(brief, REVIEWER_ONE, "CHANGES_NEEDED", "Round one comment.").andExpect(status().isCreated());
        mockMvc.perform(json(post("/cases/{id}/my-review/follow-up-notes", brief.getId()),
                        Map.of("note", "Round one note.")).with(reviewer(REVIEWER_ONE)))
                .andExpect(status().isCreated());
        return brief;
    }

    private void askAnswerAccept(Submission brief) throws Exception {
        askAndAnswer(brief);
        entityManager.flush();
        long revisionId = jdbcTemplate.queryForObject(
                "SELECT id FROM case_revisions WHERE brief_id = ? ORDER BY version_number DESC LIMIT 1",
                Long.class, brief.getId());
        mockMvc.perform(json(post("/cases/{id}/revisions/{revisionId}/accept", brief.getId(), revisionId),
                        Map.of("caseVersion", caseVersion(brief))).with(coordinator()))
                .andExpect(status().isOk());
    }

    private void askAndAnswer(Submission brief) throws Exception {
        ask(brief);
        long requestVersion = json(body(mockMvc.perform(get("/cases/{id}/my-improvement", brief.getId())
                .with(user(OWNER).roles("SUBMITTER"))).andExpect(status().isOk())))
                .get("openRequest").get("version").asLong();
        // Main's improved-version takes ReplySubmissionRequest: the what-changed text is "note". Main has no
        // request version, so requestVersion (read above, as before) is no longer sent.
        mockMvc.perform(json(post("/cases/{id}/improved-version", brief.getId()),
                        Map.of("note", "Improved."))
                        .with(user(OWNER).roles("SUBMITTER")))
                .andExpect(status().isCreated());
    }

    private void ask(Submission brief) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("guidanceNote", "Please improve.");
        body.put("dueDate", today().plusDays(10).toString());
        body.put("requestedChanges", List.of("One change"));
        body.put("version", caseVersion(brief));
        mockMvc.perform(json(post("/cases/{id}/refinement-requests", brief.getId()), body).with(coordinator()))
                .andExpect(status().isCreated());
    }

    private ResultActions outcome(Submission brief) throws Exception {
        return mockMvc.perform(json(put("/cases/{id}/outcome", brief.getId()), Map.of(
                "decision", "Closed with a reason", "note", "Closing after review.",
                "version", caseVersion(brief))).with(coordinator()));
    }

    private ResultActions assign(Submission brief, String reviewer) throws Exception {
        return mockMvc.perform(json(post("/cases/{id}/reviewers", brief.getId()), Map.of("reviewerId", reviewer))
                .with(coordinator()));
    }

    private ResultActions submitReview(Submission brief, String reviewer, String decision, String comment)
            throws Exception {
        long version = json(body(mockMvc.perform(get("/cases/{id}/my-review", brief.getId()).with(reviewer(reviewer)))
                .andExpect(status().isOk()))).get("assignmentVersion").asLong();
        return mockMvc.perform(json(post("/cases/{id}/reviews", brief.getId()),
                Map.of("decision", decision, "comment", comment, "assignmentVersion", version)).with(reviewer(reviewer)));
    }

    private ResultActions start(Submission brief, RequestPostProcessor caller, Map<String, Object> body)
            throws Exception {
        return mockMvc.perform(json(post(ROUNDS, brief.getId()), body).with(caller));
    }

    private static Map<String, Object> body(String mode, Integer reviewsNeeded, String dueDate, Long settingsVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("reviewsNeeded", reviewsNeeded);
        body.put("dueDate", dueDate);
        body.put("settingsVersion", settingsVersion);
        return body;
    }

    private long settingsVersion(Submission brief) throws Exception {
        entityManager.flush();
        return json(body(mockMvc.perform(get("/cases/{id}/reviewers", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()))).get("version").asLong();
    }

    private long caseVersion(Submission brief) throws Exception {
        entityManager.flush();
        return json(body(mockMvc.perform(get("/cases/{id}", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()))).get("submission").get("version").asLong();
    }

    private int roundRows(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM review_settings WHERE brief_id = ?", Integer.class,
                brief.getId());
    }

    private String stage(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT stage FROM submissions WHERE id = ?", String.class, brief.getId());
    }

    /** A brief owned by OWNER (with email) in {@code stage}, with a completed fact-finding check. */
    private Submission brief(SubmissionStage stage) {
        Submission brief = new Submission(UUID.randomUUID(), SubmissionCategory.GENERAL, "Round brief", "Purpose",
                "Problem", "receptionist@example.test", OWNER, null, null, null, null,
                Instant.parse("2026-11-01T08:00:00Z"));
        brief.updateSubmitterEmail(OWNER);
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-RD-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        for (SubmissionStage next : List.of(SubmissionStage.FORWARDED, SubmissionStage.IN_FACT_FINDING,
                SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW)) {
            brief.transitionTo(next, time);
            if (next == stage) {
                break;
            }
        }
        Submission saved = submissions.saveAndFlush(brief);
        FactFindingCheck check = new FactFindingCheck(saved.getId(), 1, "officer@example.test");
        check.complete(FactFindingResult.READY_FOR_REVIEW, Instant.parse("2026-11-01T10:30:00Z"));
        factFinding.saveAndFlush(check);
        return saved;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    private static LocalDate today() {
        return LocalDate.now(HUB_ZONE);
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }

    private static RequestPostProcessor reviewer(String email) {
        return user(email).roles("REVIEWER");
    }
}
