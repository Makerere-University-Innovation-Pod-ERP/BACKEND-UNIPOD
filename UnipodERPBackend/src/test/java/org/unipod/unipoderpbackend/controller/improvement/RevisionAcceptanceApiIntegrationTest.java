package org.unipod.unipoderpbackend.controller.improvement;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RevisionAcceptanceApiIntegrationTest {
    private static final String ACCEPT = "/cases/{id}/revisions/{revisionId}/accept";
    private static final String COORDINATOR = "coord@example.test";
    private static final String OWNER = "accept-owner@example.test";
    private static final String DONE_REVIEWER = "done-reviewer@example.test";
    private static final String WAITING_REVIEWER = "waiting-reviewer@example.test";
    private static final String NOTE = "Improved version accepted; returned to review.";
    private static final String MARKER = "SECRET-MARKER-xyz";
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private FactFindingRepository factFinding;

    @Autowired
    private ReviewSettingsRepository reviewSettings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- 1, 2: success --------------------------------------------------------------------

    @Test
    void coordinatorAcceptsTheLatestRevision() throws Exception {
        Seeded seeded = resubmittedBrief("Guidance.", "What I changed.");
        int historyBefore = historyRows(seeded.brief());

        accept(seeded, coordinator(), caseVersion(seeded.brief()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(seeded.brief().getId().toString()))
                .andExpect(jsonPath("$.revisionId").value(seeded.revisionId()))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.briefStage").value("IN_REVIEW"))
                .andExpect(jsonPath("$.acceptedBy").value(COORDINATOR))
                .andExpect(jsonPath("$.acceptedAt").value(notNullValue()));

        assertEquals("IN_REVIEW", stage(seeded.brief()));
        assertEquals(historyBefore + 1, historyRows(seeded.brief()));
        assertEquals(Map.of("from_stage", "RESUBMITTED", "to_stage", "IN_REVIEW", "note", NOTE, "changed_by", COORDINATOR),
                jdbcTemplate.queryForMap("""
                        SELECT from_stage, to_stage, note, changed_by FROM case_stage_history
                        WHERE submission_id = ? ORDER BY changed_at DESC LIMIT 1
                        """, seeded.brief().getId()));
        assertEquals(1, myAuditRows(seeded.brief()));
    }

    @Test
    void administratorCanAccept() throws Exception {
        Seeded seeded = resubmittedBrief("Guidance.", "Changed.");
        accept(seeded, user("admin@example.test").roles("ADMINISTRATOR"), caseVersion(seeded.brief()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedBy").value("admin@example.test"));
    }

    // ---- 3, 4: which revision ---------------------------------------------------------

    @Test
    void aRevisionOfAnotherBriefAndAnUnknownRevisionGiveTheSame404() throws Exception {
        Seeded mine = resubmittedBrief("Guidance.", "Mine.");
        Seeded other = resubmittedBrief("Guidance.", "Theirs.");
        long version = caseVersion(mine.brief());
        Snapshot before = snapshot(mine.brief());

        for (long revisionId : List.of(other.revisionId(), 999_999_999L)) {
            mockMvc.perform(json(post(ACCEPT, mine.brief().getId(), revisionId), body(version)).with(coordinator()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("That revision does not exist for this brief."));
        }
        assertEquals(before, snapshot(mine.brief()));
    }

    @Test
    void anOlderRevisionCannotBeAcceptedOnceANewerOneExists() throws Exception {
        Seeded first = resubmittedBrief("Round one.", "Answer one.");
        accept(first, coordinator(), caseVersion(first.brief())).andExpect(status().isOk());
        ask(first.brief(), "Round two.");
        answer(first.brief(), "Answer two.", requestVersion(first.brief()));
        Snapshot before = snapshot(first.brief());

        accept(first, coordinator(), caseVersion(first.brief()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A newer version has been submitted."));
        assertEquals(before, snapshot(first.brief()));

        long latest = latestRevisionId(first.brief());
        mockMvc.perform(json(post(ACCEPT, first.brief().getId(), latest), body(caseVersion(first.brief())))
                        .with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(3));
    }

    // ---- 5: stage -------------------------------------------------------------------------

    @Test
    void aSecondAcceptIsAConflictAndWritesNothing() throws Exception {
        Seeded seeded = resubmittedBrief("Guidance.", "Changed.");
        accept(seeded, coordinator(), caseVersion(seeded.brief())).andExpect(status().isOk());
        Snapshot before = snapshot(seeded.brief());

        accept(seeded, coordinator(), caseVersion(seeded.brief()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("There is no improved version waiting for review."));
        assertEquals(before, snapshot(seeded.brief()));
        assertEquals(1, myAuditRows(seeded.brief()));
    }

    @Test
    void briefAskedAgainAndNotYetAnsweredIsAConflict() throws Exception {
        Seeded seeded = resubmittedBrief("Round one.", "Answer one.");
        accept(seeded, coordinator(), caseVersion(seeded.brief())).andExpect(status().isOk());
        ask(seeded.brief(), "Round two.");
        assertEquals("IMPROVEMENT_REQUESTED", stage(seeded.brief()));
        Snapshot before = snapshot(seeded.brief());

        accept(seeded, coordinator(), caseVersion(seeded.brief()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("There is no improved version waiting for review."));
        assertEquals(before, snapshot(seeded.brief()));
    }

    // ---- 6: case version ------------------------------------------------------------------

    @Test
    void missingCaseVersionIsUnprocessableAndAStaleOneIsTheTransitionsConflict() throws Exception {
        Seeded seeded = resubmittedBrief("Guidance.", "Changed.");
        long version = caseVersion(seeded.brief());
        Snapshot before = snapshot(seeded.brief());

        accept(seeded, coordinator(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("caseVersion")));
        accept(seeded, coordinator(), version + 3)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This case was changed by another user. Reload it before saving."));
        assertEquals(before, snapshot(seeded.brief()));
        assertEquals("RESUBMITTED", stage(seeded.brief()));
    }

    // ---- 7: security and request shape ---------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"FACT_FINDING_OFFICER", "REVIEWER", "SUBMITTER", "RECEPTIONIST", "SECTOR_OFFICER",
            "IP_LIAISON", "OBSERVER"})
    void otherRolesAreForbidden(String role) throws Exception {
        Seeded seeded = resubmittedBrief("Guidance.", "Changed.");
        Snapshot before = snapshot(seeded.brief());

        accept(seeded, user(OWNER).roles(role), caseVersion(seeded.brief()))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Forbidden"));
        assertEquals(before, snapshot(seeded.brief()));
    }

    @Test
    void unauthenticatedUnknownAndMalformedRequests() throws Exception {
        mockMvc.perform(json(post(ACCEPT, UNKNOWN_BRIEF, 1L), body(0L))).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post(ACCEPT, UNKNOWN_BRIEF, 1L), body(0L)).with(coordinator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Submission not found"));
        mockMvc.perform(json(post("/cases/not-a-uuid/revisions/1/accept"), body(0L)).with(coordinator()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/cases/{id}/revisions/not-a-number/accept", UNKNOWN_BRIEF), body(0L))
                        .with(coordinator()))
                .andExpect(status().isBadRequest());
        Seeded seeded = resubmittedBrief("Guidance.", "Changed.");
        mockMvc.perform(post(ACCEPT, seeded.brief().getId(), seeded.revisionId()).with(coordinator())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"caseVersion\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
        assertEquals("RESUBMITTED", stage(seeded.brief()));
    }

    @Test
    void serverOwnedFieldsAreIgnored() throws Exception {
        Seeded seeded = resubmittedBrief("Guidance.", "Changed.");
        Map<String, Object> body = body(caseVersion(seeded.brief()));
        body.put("acceptedBy", "attacker@example.test");
        body.put("acceptedAt", "2000-01-01T00:00:00Z");
        body.put("versionNumber", 99);

        mockMvc.perform(json(post(ACCEPT, seeded.brief().getId(), seeded.revisionId()), body).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedBy").value(COORDINATOR))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.acceptedAt").value(org.hamcrest.Matchers.not(containsString("2000-01-01"))));
    }

    // ---- 9: audit -------------------------------------------------------------------------

    @Test
    void auditHasOnlyCycleAndVersionAndNoFreeText() throws Exception {
        Seeded seeded = resubmittedBrief("Guidance " + MARKER, "Changed " + MARKER);
        accept(seeded, coordinator(), caseVersion(seeded.brief())).andExpect(status().isOk());
        entityManager.flush();

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT actor, entity_type, before_value::text AS before_value, after_value::text AS after_value
                FROM audit_log WHERE entity_id = ? AND action = 'submission.improved_version_accepted'
                """, seeded.brief().getId());
        assertEquals(COORDINATOR, row.get("actor"));
        assertEquals("improvement", row.get("entity_type"));
        JsonNode details = objectMapper.readTree((String) row.get("after_value"));
        assertEquals(Set.of("cycle", "versionNumber"), new HashSet<>(details.propertyNames()));
        assertEquals(1, details.get("cycle").asInt());
        assertEquals(2, details.get("versionNumber").asInt());
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE action IN ('submission.improved_version_accepted', 'submission.improved_version_submitted',
                                 'submission.improvement_requested')
                  AND concat_ws('|', actor, actor_role, action, entity_type, entity_id::text,
                                before_value::text, after_value::text, department_id::text, request_id) LIKE ?
                """, Integer.class, "%" + MARKER + "%"));
    }

    // ---- 12, 13: the stale review round (documented, not fixed) --------------------------

    @Test
    void afterAcceptingTheBriefIsBackInItsOldReviewRound() throws Exception {
        Seeded seeded = resubmittedBrief("Guidance.", "Changed.");
        String quorumBefore = body(mockMvc.perform(get("/cases/{id}/review-quorum", seeded.brief().getId())
                .with(coordinator())).andExpect(status().isOk()));

        accept(seeded, coordinator(), caseVersion(seeded.brief())).andExpect(status().isOk());

        mockMvc.perform(get("/cases/{id}/refinement-history", seeded.brief().getId()).with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests[0].revision.versionNumber").value(2));
        // The reviewer who submitted in round 1 still shows SUBMITTED; the one who had not can submit now.
        mockMvc.perform(get("/queues/reviews").with(user(DONE_REVIEWER).roles("REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items[0].briefId").value(seeded.brief().getId().toString()))
                .andExpect(jsonPath("$.rows.items[0].round").value(1))
                .andExpect(jsonPath("$.rows.items[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.rows.items[0].stage").value("IN_REVIEW"))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(false));
        mockMvc.perform(get("/queues/reviews").with(user(WAITING_REVIEWER).roles("REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items[0].round").value(1))
                .andExpect(jsonPath("$.rows.items[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.rows.items[0].canSubmit").value(true));
        // The quorum is still round 1's: unchanged by accepting (known gap, next step).
        String quorumAfter = body(mockMvc.perform(get("/cases/{id}/review-quorum", seeded.brief().getId())
                .with(coordinator())).andExpect(status().isOk()));
        assertEquals(json(quorumBefore), json(quorumAfter));
        assertEquals(1, json(quorumAfter).get("round").asInt());
        assertEquals(1, json(quorumAfter).get("completed").asInt());
    }

    // ---- 14: contract ---------------------------------------------------------------------

    @Test
    void responseFieldNamesMatchTheRecord() throws Exception {
        Seeded seeded = resubmittedBrief("Guidance.", "Changed.");
        JsonNode response = json(body(accept(seeded, coordinator(), caseVersion(seeded.brief()))
                .andExpect(status().isOk())));
        assertEquals(Set.of("briefId", "revisionId", "versionNumber", "briefStage", "acceptedBy", "acceptedAt"),
                new HashSet<>(response.propertyNames()));
        assertFalse(response.toString().contains(MARKER));
    }

    // ---- helpers --------------------------------------------------------------------------

    record Seeded(Submission brief, long revisionId) {
    }

    record Snapshot(String stage, int history, int auditRows, int revisions, String requestStatus) {
    }

    private Snapshot snapshot(Submission brief) {
        entityManager.flush();
        List<String> status = jdbcTemplate.queryForList(
                "SELECT status FROM refinement_requests WHERE brief_id = ? ORDER BY cycle DESC LIMIT 1",
                String.class, brief.getId());
        return new Snapshot(stage(brief), historyRows(brief),
                jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ?", Integer.class,
                        brief.getId()),
                jdbcTemplate.queryForObject("SELECT count(*) FROM case_revisions WHERE brief_id = ?", Integer.class,
                        brief.getId()),
                status.isEmpty() ? null : status.getFirst());
    }

    /**
     * An IN_REVIEW brief with a completed fact-finding check and review round 1 (one reviewer submitted, one not),
     * on which the coordinator asked for changes and the owner answered: RESUBMITTED with revision 2.
     */
    private Seeded resubmittedBrief(String guidance, String whatChanged) throws Exception {
        Submission brief = submissions.saveAndFlush(ImprovementTestBriefs.ownedBy(OWNER, SubmissionStage.IN_REVIEW,
                "CASE-AC-"));
        FactFindingCheck check = new FactFindingCheck(brief.getId(), 1, "officer@example.test");
        check.complete(FactFindingResult.READY_FOR_REVIEW, Instant.parse("2026-11-10T09:00:00Z"));
        factFinding.saveAndFlush(check);
        reviewSettings.saveAndFlush(new ReviewSettings(brief.getId(), 1, ReviewMode.PARALLEL, 2, null, COORDINATOR));
        ReviewAssignment done = new ReviewAssignment(brief.getId(), 1, DONE_REVIEWER, null, 1, COORDINATOR);
        done.setStatus(ReviewAssignmentStatus.SUBMITTED);
        done = assignments.saveAndFlush(done);
        reviews.saveAndFlush(new Review(done.getId(), ReviewDecision.CHANGES_NEEDED, "Needs a budget.",
                Instant.parse("2026-11-11T09:00:00Z")));
        assignments.saveAndFlush(new ReviewAssignment(brief.getId(), 1, WAITING_REVIEWER, null, 2, COORDINATOR));

        ask(brief, guidance);
        answer(brief, whatChanged, requestVersion(brief));
        assertEquals("RESUBMITTED", stage(brief));
        return new Seeded(brief, latestRevisionId(brief));
    }

    private void ask(Submission brief, String guidance) throws Exception {
        mockMvc.perform(json(post("/cases/{id}/refinement-requests", brief.getId()),
                        ImprovementTestBodies.body(guidance, LocalDate.now(HUB_ZONE).plusDays(10),
                                List.of("Change one"), caseVersion(brief)))
                        .with(coordinator()))
                .andExpect(status().isCreated());
    }

    private void answer(Submission brief, String whatChanged, long requestVersion) throws Exception {
        mockMvc.perform(json(post("/cases/{id}/improved-version", brief.getId()),
                        ImprovementTestBodies.answerBody(whatChanged, requestVersion))
                        .with(user(OWNER).roles("SUBMITTER")))
                .andExpect(status().isCreated());
    }

    private long requestVersion(Submission brief) throws Exception {
        return json(body(mockMvc.perform(get("/cases/{id}/my-improvement", brief.getId())
                        .with(user(OWNER).roles("SUBMITTER"))).andExpect(status().isOk())))
                .get("openRequest").get("version").asLong();
    }

    private long latestRevisionId(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "SELECT id FROM case_revisions WHERE brief_id = ? ORDER BY version_number DESC LIMIT 1",
                Long.class, brief.getId());
    }

    private ResultActions accept(Seeded seeded, RequestPostProcessor caller, Long caseVersion) throws Exception {
        return mockMvc.perform(json(post(ACCEPT, seeded.brief().getId(), seeded.revisionId()), body(caseVersion))
                .with(caller));
    }

    private static Map<String, Object> body(Long caseVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseVersion", caseVersion);
        return body;
    }

    private long caseVersion(Submission brief) throws Exception {
        entityManager.flush();
        return json(body(mockMvc.perform(get("/cases/{id}", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()))).get("submission").get("version").asLong();
    }

    private int myAuditRows(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'submission.improved_version_accepted'",
                Integer.class, brief.getId());
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

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }
}
