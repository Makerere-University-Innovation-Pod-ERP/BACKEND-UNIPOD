package org.unipod.unipoderpbackend.controller.review;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewFollowUpNoteApiIntegrationTest {
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000c0de");
    private static final String NOTES = "/cases/{id}/my-review/follow-up-notes";
    private static final String COORDINATOR = "coord@example.test";
    private static final String REVIEWER = "reviewer@example.test";
    private static final String OTHER = "other-reviewer@example.test";
    private static final String MARKER = "SECRET-MARKER-xyz";
    private static final String NOT_SUBMITTED = "Submit your review before adding a follow-up note.";
    private static final String LIMIT = "The follow-up note limit has been reached.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- 1: success ----------------------------------------------------------------------

    @Test
    void reviewerWithASubmittedReviewAddsANoteAndTheReviewIsUntouched() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        Map<String, Object> reviewBefore = reviewRow(brief, REVIEWER);
        Instant before = Instant.now().minusSeconds(5);

        String body = body(addNote(brief, REVIEWER, "   Please also check the budget.   ")
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.note").value("Please also check the budget."))
                .andExpect(jsonPath("$.createdBy").value(REVIEWER))
                .andExpect(jsonPath("$.createdAt").value(notNullValue())));
        JsonNode response = json(body);

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT id, note, created_by, created_at FROM review_follow_up_notes WHERE review_id = ?",
                reviewId(brief, REVIEWER));
        assertEquals(response.get("id").asLong(), ((Number) stored.get("id")).longValue());
        assertEquals("Please also check the budget.", stored.get("note"));
        assertEquals(REVIEWER, stored.get("created_by"));
        Instant createdAt = ((Timestamp) stored.get("created_at")).toInstant();
        assertTrue(createdAt.isAfter(before));
        assertEquals(createdAt, Instant.parse(response.get("createdAt").asString()), "response matches stored value");
        assertEquals(1, countNotes(brief, REVIEWER));
        assertEquals(reviewBefore, reviewRow(brief, REVIEWER));
    }

    // ---- 2: ordering ---------------------------------------------------------------------

    @Test
    void notesComeBackInCreatedOrderWithIdBreakingTies() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        List<Long> ids = new ArrayList<>();
        for (String note : List.of("first", "second", "third")) {
            ids.add(json(body(addNote(brief, REVIEWER, note).andExpect(status().isCreated()))).get("id").asLong());
        }

        JsonNode listed = json(body(listNotes(brief, REVIEWER).andExpect(status().isOk())));
        assertTrue(listed.isArray());
        assertEquals(ids, idsOf(listed));
        assertEquals(List.of("first", "second", "third"), textOf(listed, "note"));

        // Same instant for all three: the id decides.
        entityManager.flush();
        jdbcTemplate.update("UPDATE review_follow_up_notes SET created_at = ? WHERE review_id = ?",
                Timestamp.from(Instant.parse("2026-11-12T10:00:00Z")), reviewId(brief, REVIEWER));
        entityManager.clear();
        assertEquals(ids, idsOf(json(body(listNotes(brief, REVIEWER).andExpect(status().isOk())))));

        // An older timestamp on the newest note moves it first.
        jdbcTemplate.update("UPDATE review_follow_up_notes SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-11-12T09:00:00Z")), ids.get(2));
        assertEquals(List.of(ids.get(2), ids.get(0), ids.get(1)),
                idsOf(json(body(listNotes(brief, REVIEWER).andExpect(status().isOk())))));
    }

    // ---- 3, 4, 5: rules ------------------------------------------------------------------

    @Test
    void reviewerWhoHasNotSubmittedCannotAddNotesAndSeesAnEmptyList() throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", REVIEWER);

        addNote(brief, REVIEWER, "Too early.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Submission conflict"))
                .andExpect(jsonPath("$.detail").value(NOT_SUBMITTED));
        listNotes(brief, REVIEWER)
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        assertEquals(0, totalNotes());
    }

    @Test
    void noteIsValidatedAfterTrimming() throws Exception {
        Submission brief = submittedBrief(REVIEWER);

        for (String invalid : List.of("", "   \t  ")) {
            addNote(brief, REVIEWER, invalid)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("note")));
        }
        mockMvc.perform(withBody(post(NOTES, brief.getId()), Map.of()).with(reviewer(REVIEWER)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("note")));
        addNote(brief, REVIEWER, "x".repeat(1001))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("note")));
        assertEquals(0, countNotes(brief, REVIEWER));

        addNote(brief, REVIEWER, "y".repeat(1000)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("y".repeat(1000)));
        addNote(brief, REVIEWER, "   " + "z".repeat(1000) + "   ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("z".repeat(1000)));
        assertEquals(2, countNotes(brief, REVIEWER));
    }

    @Test
    void twentyNotesAreAcceptedAndTheTwentyFirstIsAConflict() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        for (int i = 1; i <= 20; i++) {
            addNote(brief, REVIEWER, "Note " + i).andExpect(status().isCreated());
        }
        addNote(brief, REVIEWER, "Note 21")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(LIMIT));
        assertEquals(20, countNotes(brief, REVIEWER));
        listNotes(brief, REVIEWER).andExpect(jsonPath("$", hasSize(20)));
    }

    // ---- 6, 7, 8: not assigned, older round, stage ---------------------------------------

    @Test
    void notAssignedLooksExactlyLikeAnUnknownBrief() throws Exception {
        Submission assigned = submittedBrief(OTHER);
        Submission noSettings = underReviewBrief();

        JsonNode unknownPost = json(body(addNote(UNKNOWN_BRIEF, REVIEWER, "x").andExpect(status().isNotFound())));
        JsonNode unknownGet = json(body(listNotes(UNKNOWN_BRIEF, REVIEWER).andExpect(status().isNotFound())));
        for (Submission brief : List.of(assigned, noSettings)) {
            JsonNode post = json(body(addNote(brief, REVIEWER, "x").andExpect(status().isNotFound())));
            JsonNode get = json(body(listNotes(brief, REVIEWER).andExpect(status().isNotFound())));
            assertEquals(withoutInstance(unknownPost), withoutInstance(post));
            assertEquals(withoutInstance(unknownGet), withoutInstance(get));
        }
        assertEquals("Submission not found", unknownPost.get("title").asString());
        assertEquals("The requested submission was not found.", unknownPost.get("detail").asString());
        assertEquals(0, totalNotes());
    }

    @Test
    void assignmentOnlyInAnOlderRoundIsNotFound() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        settings.saveAndFlush(new ReviewSettings(brief.getId(), 2, ReviewMode.PARALLEL, 1, null, COORDINATOR));

        addNote(brief, REVIEWER, "Old round.").andExpect(status().isNotFound());
        listNotes(brief, REVIEWER).andExpect(status().isNotFound());
        assertEquals(0, totalNotes());
    }

    @Test
    void briefNotUnderReviewRejectsNewNotesButStillLists() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        addNote(brief, REVIEWER, "Before refinement.").andExpect(status().isCreated());
        Submission reloaded = submissions.findById(brief.getId()).orElseThrow();
        reloaded.transitionTo(SubmissionStage.REFINEMENT_REQUESTED, Instant.parse("2026-11-12T10:00:00Z"));
        submissions.saveAndFlush(reloaded);

        addNote(brief, REVIEWER, "After refinement.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("under review")));
        listNotes(brief, REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].note").value("Before refinement."));
    }

    // ---- 9, 10: isolation and identity ---------------------------------------------------

    @Test
    void anotherReviewersNotesNeverAppear() throws Exception {
        Submission brief = submittedBrief(REVIEWER, OTHER);
        addNote(brief, OTHER, "Other reviewer's private follow-up.").andExpect(status().isCreated());
        addNote(brief, REVIEWER, "Mine.").andExpect(status().isCreated());

        String mine = body(listNotes(brief, REVIEWER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].note").value("Mine.")));
        assertFalse(mine.contains(OTHER), mine);
        assertFalse(mine.contains("private follow-up"), mine);
    }

    @Test
    void tokenEmailWithDifferentLetterCaseStillMatches() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        String shouted = "Reviewer@EXAMPLE.Test";

        addNote(brief, shouted, "Case-insensitive.").andExpect(status().isCreated());
        listNotes(brief, shouted).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    // ---- 11: security and request shape --------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "INNOVATION_COORDINATOR", "ADMINISTRATOR", "FACT_FINDING_OFFICER", "SUBMITTER",
            "RECEPTIONIST", "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"
    })
    void otherRolesAreForbiddenOnBothEndpoints(String role) throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        RequestPostProcessor other = user(REVIEWER).roles(role);
        int auditBefore = auditRows();

        for (MockHttpServletRequestBuilder request : List.of(
                withBody(post(NOTES, brief.getId()), Map.of("note", "Not mine.")),
                get(NOTES, brief.getId()))) {
            mockMvc.perform(request.with(other))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.title").value("Forbidden"))
                    .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
        }
        assertEquals(0, totalNotes());
        assertEquals(auditBefore, auditRows());
    }

    @Test
    void unauthenticatedMalformedAndUnknownRequests() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                withBody(post(NOTES, UNKNOWN_BRIEF), Map.of("note", "x")), get(NOTES, UNKNOWN_BRIEF))) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
        }
        for (MockHttpServletRequestBuilder request : List.of(
                withBody(post("/cases/not-a-uuid/my-review/follow-up-notes"), Map.of("note", "x")),
                get("/cases/not-a-uuid/my-review/follow-up-notes"))) {
            mockMvc.perform(request.with(reviewer(REVIEWER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"));
        }
        Submission brief = submittedBrief(REVIEWER);
        mockMvc.perform(post(NOTES, brief.getId()).with(reviewer(REVIEWER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed request body"));
        addNote(UNKNOWN_BRIEF, REVIEWER, "x").andExpect(status().isNotFound());
        assertEquals(0, totalNotes());
    }

    @Test
    void serverOwnedFieldsInTheBodyAreIgnored() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("note", "Honest note.");
        body.put("id", 999999);
        body.put("reviewId", 424242);
        body.put("createdBy", "attacker@example.test");
        body.put("createdAt", "2000-01-01T00:00:00Z");

        String response = body(mockMvc.perform(withBody(post(NOTES, brief.getId()), body).with(reviewer(REVIEWER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(REVIEWER)));
        JsonNode note = json(response);
        assertFalse(note.get("id").asLong() == 999999L);
        assertFalse(note.get("createdAt").asString().startsWith("2000-"));
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT review_id, created_by FROM review_follow_up_notes WHERE id = ?", note.get("id").asLong());
        assertEquals(reviewId(brief, REVIEWER), ((Number) stored.get("review_id")).longValue());
        assertEquals(REVIEWER, stored.get("created_by"));
    }

    // ---- 13: side effects ----------------------------------------------------------------

    @Test
    void stageAndCoordinatorPanelAreUnchanged() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        String panelBefore = body(mockMvc.perform(get("/cases/{id}/reviewers", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()));

        addNote(brief, REVIEWER, "Follow-up " + MARKER).andExpect(status().isCreated());

        assertEquals("UNDER_REVIEW", jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?", String.class, brief.getId()));
        String panelAfter = body(mockMvc.perform(get("/cases/{id}/reviewers", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()));
        assertEquals(json(panelBefore), json(panelAfter));
        assertFalse(panelAfter.contains(MARKER));
    }

    // ---- 14, 15: audit -------------------------------------------------------------------

    @Test
    void eachNoteWritesOneAuditRowWithoutTheNoteText() throws Exception {
        Submission brief = submittedBrief(REVIEWER);

        for (int i = 0; i < 2; i++) {
            int before = auditRows();
            addNote(brief, REVIEWER, "Note " + i + " " + MARKER).andExpect(status().isCreated());
            assertEquals(before + 1, auditRows());
        }
        entityManager.flush();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT actor, actor_role, entity_type, entity_id, before_value::text AS before_value,
                       after_value::text AS after_value
                FROM audit_log WHERE entity_id = ? AND action = 'submission.review_follow_up_added'
                ORDER BY occurred_at, id
                """, brief.getId());
        assertEquals(2, rows.size());
        for (Map<String, Object> row : rows) {
            assertEquals(REVIEWER, row.get("actor"));
            assertEquals("REVIEWER", row.get("actor_role"));
            assertEquals("review", row.get("entity_type"));
            assertEquals(brief.getId(), row.get("entity_id"));
            assertNull(row.get("before_value"));
            JsonNode details = json((String) row.get("after_value"));
            assertEquals(Set.of("round", "position"), new HashSet<>(details.propertyNames()));
            assertEquals(1, details.get("round").asInt());
            assertEquals(1, details.get("position").asInt());
        }

        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM audit_log
                WHERE concat_ws('|', actor, actor_role, action, entity_type, entity_id::text,
                                before_value::text, after_value::text, department_id::text, request_id) LIKE ?
                """, Integer.class, "%" + MARKER + "%"));
        for (MockHttpServletRequestBuilder feed : List.of(
                get("/cases/{id}/activity", brief.getId()),
                get("/activity").param("entityType", "review").param("entityId", brief.getId().toString()))) {
            String response = body(mockMvc.perform(feed.with(user("admin@example.test").roles("ADMINISTRATOR")))
                    .andExpect(status().isOk()));
            assertFalse(response.contains(MARKER), response);
        }
    }

    @Test
    void rejectedRequestsWriteNoAuditRow() throws Exception {
        Submission notSubmitted = briefWithReviewers("PARALLEL", REVIEWER);
        Submission submitted = submittedBrief(OTHER);

        expectNoAudit(() -> addNote(notSubmitted, REVIEWER, "x", user(REVIEWER).roles("INNOVATION_COORDINATOR")),
                status().isForbidden());
        expectNoAudit(() -> addNote(UNKNOWN_BRIEF, REVIEWER, "x"), status().isNotFound());
        expectNoAudit(() -> addNote(submitted, REVIEWER, "x"), status().isNotFound());
        expectNoAudit(() -> addNote(notSubmitted, REVIEWER, "x"), status().isConflict());
        expectNoAudit(() -> addNote(submitted, OTHER, "   "), status().isUnprocessableEntity());
        for (int i = 0; i < 20; i++) {
            addNote(submitted, OTHER, "n" + i).andExpect(status().isCreated());
        }
        expectNoAudit(() -> addNote(submitted, OTHER, "21st"), status().isConflict());
    }

    // ---- 18, 19: contract and read-only --------------------------------------------------

    @Test
    void jsonFieldNamesMatchTheRecordExactly() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        JsonNode created = json(body(addNote(brief, REVIEWER, "Shape.").andExpect(status().isCreated())));
        Set<String> expected = Set.of("id", "note", "createdBy", "createdAt");
        assertEquals(expected, new HashSet<>(created.propertyNames()));
        JsonNode listed = json(body(listNotes(brief, REVIEWER).andExpect(status().isOk())));
        assertEquals(expected, new HashSet<>(listed.get(0).propertyNames()));
        assertEquals(created, listed.get(0));
    }

    @Test
    void listingNeverWrites() throws Exception {
        Submission brief = submittedBrief(REVIEWER);
        addNote(brief, REVIEWER, "One.").andExpect(status().isCreated());
        Map<String, Object> before = snapshot();

        listNotes(brief, REVIEWER).andExpect(status().isOk());
        listNotes(brief, REVIEWER).andExpect(status().isOk());
        listNotes(UNKNOWN_BRIEF, REVIEWER).andExpect(status().isNotFound());

        assertEquals(before, snapshot());
    }

    // ---- helpers -------------------------------------------------------------------------

    @FunctionalInterface
    private interface Call {
        ResultActions run() throws Exception;
    }

    private void expectNoAudit(Call call, ResultMatcher expected) throws Exception {
        int before = auditRows();
        call.run().andExpect(expected);
        assertEquals(before, auditRows());
    }

    private ResultActions addNote(Submission brief, String principal, String note) throws Exception {
        return addNote(brief.getId(), principal, note);
    }

    private ResultActions addNote(UUID briefId, String principal, String note) throws Exception {
        return mockMvc.perform(withBody(post(NOTES, briefId), Map.of("note", note)).with(reviewer(principal)));
    }

    private ResultActions addNote(Submission brief, String principal, String note, RequestPostProcessor caller)
            throws Exception {
        return mockMvc.perform(withBody(post(NOTES, brief.getId()), Map.of("note", note)).with(caller));
    }

    private ResultActions listNotes(Submission brief, String principal) throws Exception {
        return listNotes(brief.getId(), principal);
    }

    private ResultActions listNotes(UUID briefId, String principal) throws Exception {
        return mockMvc.perform(get(NOTES, briefId).with(reviewer(principal)));
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Map<String, ?> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private static RequestPostProcessor reviewer(String email) {
        return user(email).roles("REVIEWER");
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }

    private Map<String, Object> reviewRow(Submission brief, String reviewer) {
        entityManager.flush();
        return jdbcTemplate.queryForMap("""
                SELECT r.id, r.decision, r.comment, r.submitted_at, r.version
                FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ? AND a.reviewer_id = ?
                """, brief.getId(), reviewer);
    }

    private long reviewId(Submission brief, String reviewer) {
        return ((Number) reviewRow(brief, reviewer).get("id")).longValue();
    }

    private int countNotes(Submission brief, String reviewer) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM review_follow_up_notes WHERE review_id = ?", Integer.class, reviewId(brief, reviewer));
    }

    private int totalNotes() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM review_follow_up_notes", Integer.class);
    }

    private int auditRows() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log", Integer.class);
    }

    private Map<String, Object> snapshot() {
        entityManager.flush();
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT count(*) FROM review_follow_up_notes) AS notes,
                       (SELECT count(*) FROM reviews) AS reviews,
                       (SELECT count(*) FROM review_assignments) AS assignments,
                       (SELECT count(*) FROM audit_log) AS audit,
                       (SELECT coalesce(sum(version), 0) FROM review_assignments) AS versions
                """);
    }

    private static List<Long> idsOf(JsonNode notes) {
        List<Long> ids = new ArrayList<>();
        notes.forEach(note -> ids.add(note.get("id").asLong()));
        return ids;
    }

    private static List<String> textOf(JsonNode notes, String field) {
        List<String> values = new ArrayList<>();
        notes.forEach(note -> values.add(note.get(field).asString()));
        return values;
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

    /** An UNDER_REVIEW brief where each given reviewer has submitted a review through the real endpoints. */
    private Submission submittedBrief(String... reviewers) throws Exception {
        Submission brief = briefWithReviewers("PARALLEL", reviewers);
        for (String reviewer : reviewers) {
            long version = json(body(mockMvc.perform(get("/cases/{id}/my-review", brief.getId()).with(reviewer(reviewer)))
                    .andExpect(status().isOk()))).get("assignmentVersion").asLong();
            mockMvc.perform(withBody(post("/cases/{id}/reviews", brief.getId()),
                            Map.of("decision", "RECOMMENDED", "comment", "Review " + MARKER, "assignmentVersion", version))
                            .with(reviewer(reviewer)))
                    .andExpect(status().isCreated());
        }
        return brief;
    }

    private Submission briefWithReviewers(String mode, String... reviewers) throws Exception {
        Submission brief = underReviewBrief();
        Map<String, Object> settingsBody = new LinkedHashMap<>();
        settingsBody.put("mode", mode);
        settingsBody.put("reviewsNeeded", Math.max(1, reviewers.length));
        mockMvc.perform(withBody(put("/cases/{id}/review-settings", brief.getId()), settingsBody).with(coordinator()))
                .andExpect(status().isOk());
        for (String reviewer : reviewers) {
            users.saveAndFlush(new ApiUser(UUID.randomUUID(), reviewer, "not-a-real-hash", ApiUserRole.REVIEWER,
                    Instant.parse("2026-11-01T08:00:00Z")));
            mockMvc.perform(withBody(post("/cases/{id}/reviewers", brief.getId()), Map.of("reviewerId", reviewer))
                            .with(coordinator()))
                    .andExpect(status().isCreated());
        }
        return brief;
    }

    private Submission underReviewBrief() {
        Submission brief = new Submission(
                UUID.randomUUID(), SubmissionCategory.GENERAL, "Follow-up brief", "Purpose", "Problem",
                "receptionist@example.test", "submitter@example.test", null, null, null, null,
                Instant.parse("2026-11-01T08:00:00Z"));
        brief.register("CASE-FU-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        brief.transitionTo(SubmissionStage.REGISTERED, time);
        brief.transitionTo(SubmissionStage.FACT_FINDING, time);
        brief.transitionTo(SubmissionStage.UNDER_REVIEW, time);
        return submissions.saveAndFlush(brief);
    }
}
