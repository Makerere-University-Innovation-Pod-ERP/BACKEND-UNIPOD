package org.unipod.unipoderpbackend.controller.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Audit entries for the six fact-finding and review writes: one row each, only structural values,
 * nothing for rejected requests, and nothing a submitter could use to learn about reviewers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReviewAuditIntegrationTest {
    private static final String MARKER = "SECRET-MARKER-xyz";
    private static final String OFFICER = "officer@example.test";
    private static final String COORDINATOR = "coord@example.test";
    private static final String ADMIN = "admin@example.test";
    private static final String SUBMITTER = "submitter@example.test";
    private static final String REVIEWER = "reviewer@example.test";
    private static final String REVIEWER_TWO = "reviewer-two@example.test";
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    private static final Set<String> SAVED_KEYS = Set.of("state", "round", "version");
    private static final Set<String> COMPLETED_KEYS = Set.of("round", "result", "version");
    private static final Set<String> SETTINGS_KEYS = Set.of("round", "mode", "reviewsNeeded", "dueDate");
    private static final Set<String> POSITION_KEYS = Set.of("round", "position");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- 1, 2: one row per operation with exactly the allowed details --------------------

    @Test
    void eachOfTheSixOperationsWritesExactlyOneRowWithOnlyTheAllowedDetails() throws Exception {
        Submission brief = factFindingBrief();
        reviewers();

        long version = version(expectOneRow(brief, () -> saveDraft(brief, answers(null)).andExpect(status().isOk())));
        AuditRow saved = lastRow(brief);
        assertRow(saved, "submission.fact_finding_saved", OFFICER, "FACT_FINDING_OFFICER", "submission", brief);
        assertNull(saved.before());
        assertEquals(SAVED_KEYS, keys(saved.after()));
        assertEquals("DRAFT", saved.after().get("state").asString());
        assertEquals(1, saved.after().get("round").asInt());
        assertEquals(version, saved.after().get("version").asLong());

        long completedVersion = version(expectOneRow(brief, () -> complete(brief, version).andExpect(status().isOk())));
        AuditRow completed = lastRow(brief);
        assertRow(completed, "submission.fact_finding_completed", OFFICER, "FACT_FINDING_OFFICER", "submission", brief);
        assertNull(completed.before());
        assertEquals(COMPLETED_KEYS, keys(completed.after()));
        assertEquals(1, completed.after().get("round").asInt());
        assertEquals("READY_FOR_REVIEW", completed.after().get("result").asString());
        assertEquals(completedVersion, completed.after().get("version").asLong());

        String due = today().plusDays(10).toString();
        expectOneRow(brief, () -> putSettings(brief, settingsBody("PARALLEL", 2, due, null)).andExpect(status().isOk()));
        AuditRow settings = lastRow(brief);
        assertRow(settings, "submission.review_settings_saved", COORDINATOR, "INNOVATION_COORDINATOR", "review", brief);
        assertNull(settings.before());
        assertEquals(SETTINGS_KEYS, keys(settings.after()));
        assertEquals(Map.of("round", 1, "mode", "PARALLEL", "reviewsNeeded", 2, "dueDate", due),
                asMap(settings.after()));

        expectOneRow(brief, () -> assign(brief, REVIEWER).andExpect(status().isCreated()));
        AuditRow assigned = lastRow(brief);
        assertRow(assigned, "submission.reviewer_assigned", COORDINATOR, "INNOVATION_COORDINATOR", "review", brief);
        assertNull(assigned.before());
        assertEquals(Map.of("round", 1, "position", 1), asMap(assigned.after()));

        assign(brief, REVIEWER_TWO).andExpect(status().isCreated());
        expectOneRow(brief, () -> remove(brief, REVIEWER_TWO).andExpect(status().isOk()));
        AuditRow removed = lastRow(brief);
        assertRow(removed, "submission.reviewer_removed", COORDINATOR, "INNOVATION_COORDINATOR", "review", brief);
        assertNull(removed.before());
        assertEquals(Map.of("round", 1, "position", 2), asMap(removed.after()));

        expectOneRow(brief, () -> submitReview(brief, REVIEWER, "RECOMMENDED", "Fine.").andExpect(status().isCreated()));
        AuditRow submitted = lastRow(brief);
        assertRow(submitted, "submission.review_submitted", REVIEWER, "REVIEWER", "review", brief);
        assertNull(submitted.before());
        assertEquals(POSITION_KEYS, keys(submitted.after()));
        assertEquals(Map.of("round", 1, "position", 1), asMap(submitted.after()));
    }

    @Test
    void everySuccessfulDraftSaveIsAuditedOnceEvenWhenNothingChanged() throws Exception {
        Submission brief = factFindingBrief();
        long version = version(saveDraft(brief, answers(null)).andExpect(status().isOk()));

        long same = version(expectOneRow(brief, () -> saveDraft(brief, answers(version)).andExpect(status().isOk())));
        assertEquals(version, same);
        assertEquals(2, countRows(brief, "submission.fact_finding_saved"));
    }

    // ---- 11: settings update before/after ------------------------------------------------

    @Test
    void settingsUpdateRecordsTheExactPreviousAndNewValues() throws Exception {
        Submission brief = underReviewBrief();
        String firstDue = today().plusDays(5).toString();
        long version = settingsVersion(putSettings(brief, settingsBody("PARALLEL", 2, firstDue, null))
                .andExpect(status().isOk()));

        expectOneRow(brief, () -> putSettings(brief, settingsBody("SEQUENTIAL", 3, null, version))
                .andExpect(status().isOk()));
        AuditRow updated = lastRow(brief);
        assertEquals("submission.review_settings_saved", updated.action());
        assertEquals(SETTINGS_KEYS, keys(updated.before()));
        assertEquals(SETTINGS_KEYS, keys(updated.after()));
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("round", 1);
        before.put("mode", "PARALLEL");
        before.put("reviewsNeeded", 2);
        before.put("dueDate", firstDue);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("round", 1);
        after.put("mode", "SEQUENTIAL");
        after.put("reviewsNeeded", 3);
        after.put("dueDate", null);
        assertEquals(before, asMap(updated.before()));
        assertEquals(after, asMap(updated.after()));
    }

    // ---- 3: free text never reaches the audit log or the feeds ---------------------------

    @Test
    void freeTextNeverReachesTheAuditLogOrAnyFeed() throws Exception {
        Submission brief = factFindingBrief();
        reviewers();
        Runnable noMarker = () -> {
            flush();
            assertEquals(0, jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM audit_log
                    WHERE concat_ws('|', actor, actor_role, action, entity_type, entity_id::text,
                                    before_value::text, after_value::text, department_id::text, request_id)
                          LIKE ?
                    """, Integer.class, "%" + MARKER + "%"));
        };

        long version = version(saveDraft(brief, answers(null)).andExpect(status().isOk()));
        noMarker.run();
        complete(brief, version).andExpect(status().isOk());
        noMarker.run();
        putSettings(brief, settingsBody("PARALLEL", 1, null, null)).andExpect(status().isOk());
        noMarker.run();
        assign(brief, REVIEWER).andExpect(status().isCreated());
        noMarker.run();
        assign(brief, REVIEWER_TWO).andExpect(status().isCreated());
        remove(brief, REVIEWER_TWO).andExpect(status().isOk());
        noMarker.run();
        submitReview(brief, REVIEWER, "NOT_RECOMMENDED", "Weak evidence " + MARKER).andExpect(status().isCreated());
        noMarker.run();

        // The free text really is stored (so the check above is meaningful)...
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fact_finding_checks WHERE brief_id = ? AND missing_items LIKE ?",
                Integer.class, brief.getId(), "%" + MARKER + "%"));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ? AND r.comment LIKE ?
                """, Integer.class, brief.getId(), "%" + MARKER + "%"));
        // ...but no feed shows it, for any reader.
        for (String feed : List.of(
                body(caseFeed(brief, admin())),
                body(caseFeed(brief, submitter())),
                body(globalFeed(brief, "review", admin())),
                body(globalFeed(brief, "submission", admin())))) {
            assertFalse(feed.contains(MARKER), feed);
        }
    }

    // ---- 4: rejected requests write nothing ----------------------------------------------

    @Test
    void rejectedFactFindingRequestsWriteNoAuditRow() throws Exception {
        Submission brief = factFindingBrief();
        Submission underReview = underReviewBrief();
        Map<String, Object> tooLong = answers(null);
        tooLong.put("titleNote", "x".repeat(1001));

        expectNoRow(() -> saveDraft(brief, answers(null), user(REVIEWER).roles("REVIEWER")), status().isForbidden());
        expectNoRow(() -> saveDraft(UUID.randomUUID(), answers(null)), status().isNotFound());
        expectNoRow(() -> saveDraft(underReview, answers(null)), status().isConflict());
        expectNoRow(() -> saveDraft(brief, tooLong), status().isUnprocessableEntity());

        expectNoRow(() -> complete(brief, 0L, user(REVIEWER).roles("REVIEWER")), status().isForbidden());
        expectNoRow(() -> complete(UUID.randomUUID(), 0L), status().isNotFound());
        expectNoRow(() -> complete(brief, 0L), status().isConflict()); // nothing saved yet
        saveDraft(brief, answers(null)).andExpect(status().isOk());
        expectNoRow(() -> complete(brief, null), status().isUnprocessableEntity());
    }

    @Test
    void rejectedReviewRequestsWriteNoAuditRow() throws Exception {
        Submission brief = underReviewBrief();
        Submission factFinding = factFindingBrief();
        reviewers();

        expectNoRow(() -> putSettings(brief, settingsBody("PARALLEL", 1, null, null), user(REVIEWER).roles("REVIEWER")),
                status().isForbidden());
        expectNoRow(() -> putSettings(UUID.randomUUID(), settingsBody("PARALLEL", 1, null, null)), status().isNotFound());
        expectNoRow(() -> putSettings(factFinding, settingsBody("PARALLEL", 1, null, null)), status().isConflict());
        expectNoRow(() -> putSettings(brief, settingsBody("PARALLEL", 0, null, null)), status().isUnprocessableEntity());

        expectNoRow(() -> assign(brief, REVIEWER), status().isConflict()); // settings missing
        putSettings(brief, settingsBody("PARALLEL", 1, null, null)).andExpect(status().isOk());
        expectNoRow(() -> assign(brief, REVIEWER, user(REVIEWER).roles("REVIEWER")), status().isForbidden());
        expectNoRow(() -> assign(UUID.randomUUID(), REVIEWER), status().isNotFound());
        expectNoRow(() -> assign(brief, "nobody@example.test"), status().isUnprocessableEntity());
        assign(brief, REVIEWER).andExpect(status().isCreated());
        expectNoRow(() -> assign(brief, REVIEWER), status().isConflict()); // already assigned

        expectNoRow(() -> remove(brief, REVIEWER, user(REVIEWER).roles("REVIEWER")), status().isForbidden());
        expectNoRow(() -> remove(UUID.randomUUID(), REVIEWER), status().isNotFound());
        expectNoRow(() -> remove(brief, "nobody@example.test"), status().isNotFound());

        expectNoRow(() -> submitReview(brief, REVIEWER, "RECOMMENDED", "x", user(COORDINATOR).roles("INNOVATION_COORDINATOR")),
                status().isForbidden());
        expectNoRow(() -> submitReview(brief, REVIEWER_TWO, "RECOMMENDED", "x"), status().isNotFound());
        expectNoRow(() -> submitReview(brief, REVIEWER, "RECOMMENDED", "   "), status().isUnprocessableEntity());
        submitReview(brief, REVIEWER, "RECOMMENDED", "Done.").andExpect(status().isCreated());
        expectNoRow(() -> submitReview(brief, REVIEWER, "RECOMMENDED", "Again."), status().isConflict());
        expectNoRow(() -> remove(brief, REVIEWER), status().isConflict()); // submitted reviewer
    }

    @Test
    void readsWriteNoAuditRow() throws Exception {
        Submission brief = underReviewBrief();
        reviewers();
        putSettings(brief, settingsBody("PARALLEL", 1, null, null)).andExpect(status().isOk());
        assign(brief, REVIEWER).andExpect(status().isCreated());

        expectNoRow(() -> mockMvc.perform(get("/cases/{id}/reviewers", brief.getId()).with(coordinator())), status().isOk());
        expectNoRow(() -> mockMvc.perform(get("/cases/{id}/review-quorum", brief.getId()).with(coordinator())), status().isOk());
        expectNoRow(() -> mockMvc.perform(get("/cases/{id}/my-review", brief.getId()).with(reviewer(REVIEWER))), status().isOk());
        expectNoRow(() -> mockMvc.perform(get("/queues/reviews").with(reviewer(REVIEWER))), status().isOk());
        expectNoRow(() -> mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId()).with(officer())), status().isOk());
    }

    // ---- 8, 9: brief history and what a submitter can see ---------------------------------

    @Test
    void historyShowsTheEntriesInOrderAndTheSubmitterSeesNoReviewData() throws Exception {
        Submission brief = factFindingBrief();
        reviewers();

        long version = version(saveDraft(brief, answers(null)).andExpect(status().isOk()));
        pause();
        complete(brief, version).andExpect(status().isOk());
        pause();
        putSettings(brief, settingsBody("PARALLEL", 1, null, null)).andExpect(status().isOk());
        pause();
        assign(brief, REVIEWER).andExpect(status().isCreated());
        pause();
        assign(brief, REVIEWER_TWO).andExpect(status().isCreated());
        pause();
        remove(brief, REVIEWER_TWO).andExpect(status().isOk());
        pause();
        submitReview(brief, REVIEWER, "CHANGES_NEEDED", "Needs work.").andExpect(status().isCreated());
        flush();

        JsonNode caseFeed = json(body(caseFeed(brief, admin()).andExpect(status().isOk())));
        assertEquals(List.of("submission.fact_finding_completed", "submission.fact_finding_saved"),
                field(caseFeed.get("items"), "action"));

        JsonNode reviewFeed = json(body(globalFeed(brief, "review", admin()).andExpect(status().isOk())));
        assertEquals(List.of(
                        "submission.review_submitted",
                        "submission.reviewer_removed",
                        "submission.reviewer_assigned",
                        "submission.reviewer_assigned",
                        "submission.review_settings_saved"),
                field(reviewFeed.get("items"), "action"));
        assertEquals(Set.of("review"), new HashSet<>(field(reviewFeed.get("items"), "entityType")));
        assertEquals(Set.of(brief.getId().toString()), new HashSet<>(field(reviewFeed.get("items"), "entityId")));

        String submitterView = body(caseFeed(brief, submitter()).andExpect(status().isOk()));
        JsonNode submitterItems = json(submitterView).get("items");
        assertEquals(List.of("submission.fact_finding_completed", "submission.fact_finding_saved"),
                field(submitterItems, "action"));
        for (String forbidden : List.of(REVIEWER, REVIEWER_TWO, "\"REVIEWER\"", "CHANGES_NEEDED", "Needs work",
                "\"review\"", "review_submitted", "reviewer_assigned", "reviewer_removed", "review_settings_saved")) {
            assertFalse(submitterView.contains(forbidden), forbidden + " leaked: " + submitterView);
        }
        mockMvc.perform(get("/activity").param("entityType", "review").with(submitter()))
                .andExpect(status().isForbidden());
    }

    // ---- helpers: requests ---------------------------------------------------------------

    private ResultActions saveDraft(Submission brief, Map<String, Object> body) throws Exception {
        return saveDraft(brief.getId(), body);
    }

    private ResultActions saveDraft(UUID briefId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(json(put("/cases/{id}/fact-finding", briefId), body).with(officer()));
    }

    private ResultActions saveDraft(Submission brief, Map<String, Object> body, RequestPostProcessor caller)
            throws Exception {
        return mockMvc.perform(json(put("/cases/{id}/fact-finding", brief.getId()), body).with(caller));
    }

    private ResultActions complete(Submission brief, Long version) throws Exception {
        return complete(brief.getId(), version);
    }

    private ResultActions complete(UUID briefId, Long version) throws Exception {
        return complete(briefId, version, officer());
    }

    private ResultActions complete(Submission brief, Long version, RequestPostProcessor caller) throws Exception {
        return complete(brief.getId(), version, caller);
    }

    private ResultActions complete(UUID briefId, Long version, RequestPostProcessor caller) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        return mockMvc.perform(json(post("/cases/{id}/fact-finding/complete", briefId), body).with(caller));
    }

    private ResultActions putSettings(Submission brief, Map<String, Object> body) throws Exception {
        return putSettings(brief.getId(), body);
    }

    private ResultActions putSettings(UUID briefId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(json(put("/cases/{id}/review-settings", briefId), body).with(coordinator()));
    }

    private ResultActions putSettings(Submission brief, Map<String, Object> body, RequestPostProcessor caller)
            throws Exception {
        return mockMvc.perform(json(put("/cases/{id}/review-settings", brief.getId()), body).with(caller));
    }

    private ResultActions assign(Submission brief, String reviewer) throws Exception {
        return assign(brief.getId(), reviewer, coordinator());
    }

    private ResultActions assign(UUID briefId, String reviewer) throws Exception {
        return assign(briefId, reviewer, coordinator());
    }

    private ResultActions assign(Submission brief, String reviewer, RequestPostProcessor caller) throws Exception {
        return assign(brief.getId(), reviewer, caller);
    }

    private ResultActions assign(UUID briefId, String reviewer, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(json(post("/cases/{id}/reviewers", briefId),
                Map.of("reviewerId", reviewer, "department", "Department " + MARKER)).with(caller));
    }

    private ResultActions remove(Submission brief, String reviewer) throws Exception {
        return remove(brief.getId(), reviewer, coordinator());
    }

    private ResultActions remove(UUID briefId, String reviewer) throws Exception {
        return remove(briefId, reviewer, coordinator());
    }

    private ResultActions remove(Submission brief, String reviewer, RequestPostProcessor caller) throws Exception {
        return remove(brief.getId(), reviewer, caller);
    }

    private ResultActions remove(UUID briefId, String reviewer, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(delete("/cases/{id}/reviewers/{reviewerId}", briefId, reviewer).with(caller));
    }

    private ResultActions submitReview(Submission brief, String reviewer, String decision, String comment)
            throws Exception {
        return submitReview(brief, reviewer, decision, comment, reviewer(reviewer));
    }

    private ResultActions submitReview(
            Submission brief, String reviewer, String decision, String comment, RequestPostProcessor caller)
            throws Exception {
        String myReview = body(mockMvc.perform(get("/cases/{id}/my-review", brief.getId()).with(reviewer(reviewer))));
        JsonNode mine = json(myReview);
        long assignmentVersion = mine.has("assignmentVersion") ? mine.get("assignmentVersion").asLong() : 0L;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        body.put("comment", comment);
        body.put("assignmentVersion", assignmentVersion);
        return mockMvc.perform(json(post("/cases/{id}/reviews", brief.getId()), body).with(caller));
    }

    private ResultActions caseFeed(Submission brief, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(get("/cases/{id}/activity", brief.getId()).with(caller));
    }

    private ResultActions globalFeed(Submission brief, String entityType, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(get("/activity")
                .param("entityType", entityType)
                .param("entityId", brief.getId().toString())
                .with(caller));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, Object> body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
    }

    private static Map<String, Object> answers(Long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("titleClear", true);
        body.put("titleNote", "Title note " + MARKER);
        body.put("purposeClear", true);
        body.put("purposeNote", "Purpose note " + MARKER);
        body.put("evidenceEnough", true);
        body.put("evidenceNote", "Evidence note " + MARKER);
        body.put("relevantToHub", true);
        body.put("relevantNote", "Relevant note " + MARKER);
        body.put("missingItems", "Missing " + MARKER);
        body.put("version", version);
        return body;
    }

    private static Map<String, Object> settingsBody(String mode, Integer reviewsNeeded, String dueDate, Long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("reviewsNeeded", reviewsNeeded);
        body.put("dueDate", dueDate);
        body.put("version", version);
        return body;
    }

    private static RequestPostProcessor officer() {
        return user(OFFICER).roles("FACT_FINDING_OFFICER");
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }

    private static RequestPostProcessor admin() {
        return user(ADMIN).roles("ADMINISTRATOR");
    }

    private static RequestPostProcessor submitter() {
        return user(SUBMITTER).roles("SUBMITTER");
    }

    private static RequestPostProcessor reviewer(String email) {
        return user(email).roles("REVIEWER");
    }

    // ---- helpers: audit assertions -------------------------------------------------------

    private record AuditRow(String action, String actor, String actorRole, String entityType, UUID entityId,
                            JsonNode before, JsonNode after) {
    }

    @FunctionalInterface
    private interface Call {
        ResultActions run() throws Exception;
    }

    private ResultActions expectOneRow(Submission brief, Call call) throws Exception {
        int before = totalRows();
        int beforeForBrief = briefRows(brief);
        ResultActions result = call.run();
        assertEquals(before + 1, totalRows(), "audit_log must grow by exactly one row");
        assertEquals(beforeForBrief + 1, briefRows(brief));
        return result;
    }

    private void expectNoRow(Call call, ResultMatcher expectedStatus) throws Exception {
        int before = totalRows();
        call.run().andExpect(expectedStatus);
        assertEquals(before, totalRows(), "a rejected or read-only request must not write an audit row");
    }

    private void assertRow(AuditRow row, String action, String actor, String role, String entityType, Submission brief) {
        assertEquals(action, row.action());
        assertEquals(actor, row.actor());
        assertEquals(role, row.actorRole());
        assertEquals(entityType, row.entityType());
        assertEquals(brief.getId(), row.entityId());
    }

    private AuditRow lastRow(Submission brief) {
        flush();
        return jdbcTemplate.query("""
                        SELECT action, actor, actor_role, entity_type, entity_id,
                               before_value::text AS before_value, after_value::text AS after_value
                        FROM audit_log WHERE entity_id = ? ORDER BY occurred_at DESC, id DESC LIMIT 1
                        """,
                (rs, rowNum) -> new AuditRow(
                        rs.getString("action"),
                        rs.getString("actor"),
                        rs.getString("actor_role"),
                        rs.getString("entity_type"),
                        rs.getObject("entity_id", UUID.class),
                        rs.getString("before_value") == null ? null : objectMapper.readTree(rs.getString("before_value")),
                        rs.getString("after_value") == null ? null : objectMapper.readTree(rs.getString("after_value"))),
                brief.getId()).getFirst();
    }

    private int totalRows() {
        flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log", Integer.class);
    }

    private int briefRows(Submission brief) {
        flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ?", Integer.class,
                brief.getId());
    }

    private int countRows(Submission brief, String action) {
        flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = ?",
                Integer.class, brief.getId(), action);
    }

    private void flush() {
        entityManager.flush();
    }

    /** The feed orders by time; keep the operations in distinct clock ticks so the order is deterministic. */
    private static void pause() throws InterruptedException {
        Thread.sleep(20);
    }

    private static Set<String> keys(JsonNode node) {
        return new HashSet<>(node.propertyNames());
    }

    private Map<String, Object> asMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String name : node.propertyNames()) {
            JsonNode value = node.get(name);
            map.put(name, value.isNull() ? null : value.isNumber() ? (Object) value.asInt() : value.asString());
        }
        return map;
    }

    private static List<String> field(JsonNode items, String name) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : items) {
            values.add(item.get(name).asString());
        }
        return values;
    }

    private long version(ResultActions result) throws Exception {
        return json(body(result)).get("version").asLong();
    }

    private long settingsVersion(ResultActions result) throws Exception {
        return json(body(result)).get("version").asLong();
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String body) {
        return objectMapper.readTree(body);
    }

    // ---- helpers: seeding ----------------------------------------------------------------

    private void reviewers() {
        for (String email : List.of(REVIEWER, REVIEWER_TWO)) {
            users.saveAndFlush(new ApiUser(UUID.randomUUID(), email, "not-a-real-hash", ApiUserRole.REVIEWER,
                    Instant.parse("2026-11-01T08:00:00Z")));
        }
    }

    private static LocalDate today() {
        return LocalDate.now(HUB_ZONE);
    }

    private Submission factFindingBrief() {
        return briefInStage(SubmissionStage.FACT_FINDING);
    }

    private Submission underReviewBrief() {
        return briefInStage(SubmissionStage.UNDER_REVIEW);
    }

    private Submission briefInStage(SubmissionStage target) {
        Submission brief = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                "Audited brief",
                "Purpose",
                "Problem",
                "receptionist@example.test",
                SUBMITTER,
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-01T08:00:00Z")
        );
        brief.register("CASE-AU-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        for (SubmissionStage stage : List.of(
                SubmissionStage.REGISTERED, SubmissionStage.FACT_FINDING, SubmissionStage.UNDER_REVIEW)) {
            brief.transitionTo(stage, time);
            if (stage == target) {
                break;
            }
        }
        return submissions.saveAndFlush(brief);
    }
}
