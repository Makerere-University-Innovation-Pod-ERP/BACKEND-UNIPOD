package org.unipod.unipoderpbackend.controller.factfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.sector.SectorRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Committed-data tests (no surrounding test transaction) for POST /cases/{id}/fact-finding/needs-information: the
 * audit and outbox rows one call writes, free-text safety, rejected requests writing nothing, and full rollback when
 * my audit, main's refinement insert or main's outbox insert fails. The three repository spies need their own Spring
 * context, closed after this class to free its connection pool.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class FactFindingNeedsInformationAuditTest {
    private static final String PATH = "/cases/{id}/fact-finding/needs-information";
    private static final String OFFICER = "ni-audit-officer@example.test";
    private static final String OWNER = "ni-audit-owner@example.test";
    private static final String REFINEMENT_ACTION = "submission.refinement_requested";
    private static final String COMPLETED_ACTION = "submission.fact_finding_completed";
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private SectorRepository sectors;

    @Autowired
    private ReviewSettingsRepository reviewSettings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ReviewRepository reviews;

    @MockitoSpyBean
    private AuditEntryRepository auditEntries;

    @MockitoSpyBean
    private RefinementRequestRepository refinementRequests;

    @MockitoSpyBean
    private NotificationOutbox outbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> briefs = new ArrayList<>();
    private final List<UUID> sectorIds = new ArrayList<>();

    /** Runs after every test, also a failed one: removes everything seeded or written for this class's briefs. */
    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            // audit_log is append-only (a trigger rejects DELETE); its rows stay, keyed by this random brief id.
            jdbcTemplate.update("DELETE FROM refinement_requested_changes WHERE request_id IN "
                    + "(SELECT id FROM refinement_requests WHERE brief_id = ?)", brief);
            jdbcTemplate.update("DELETE FROM case_revisions WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM refinement_requests WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM reviews WHERE assignment_id IN "
                    + "(SELECT id FROM review_assignments WHERE brief_id = ?)", brief);
            jdbcTemplate.update("DELETE FROM review_assignments WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM review_settings WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM fact_finding_checks WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM notification_outbox WHERE aggregate_id = ?", brief);
            jdbcTemplate.update("DELETE FROM case_stage_history WHERE submission_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        for (UUID sector : sectorIds) {
            jdbcTemplate.update("DELETE FROM sectors WHERE id = ?", sector);
        }
        briefs.clear();
        sectorIds.clear();
    }

    // ---- 1: exactly these rows -------------------------------------------------------------

    @Test
    void oneCallWritesExactlyTheRefinementAuditMyCompletionAuditAndOneSubmitterNotification() throws Exception {
        UUID brief = briefInFactFinding();
        long version = saveDraft(brief, Map.of("missingItems", "A costed budget"));
        Instant before = dbNow();

        needsInformation(brief, body(version, caseVersion(brief), "Please add a costed budget.",
                List.of("Budget table", "Supplier quotes")), officer()).andExpect(status().isOk());

        List<Map<String, Object>> audit = jdbcTemplate.queryForList("""
                SELECT action, entity_type, entity_id, before_value::text AS before, after_value::text AS after
                FROM audit_log WHERE occurred_at >= ? ORDER BY occurred_at
                """, Timestamp.from(before));
        assertEquals(List.of(REFINEMENT_ACTION, COMPLETED_ACTION), audit.stream().map(row -> row.get("action")).toList());
        for (Map<String, Object> row : audit) {
            assertEquals("submission", row.get("entity_type"));
            assertEquals(brief, row.get("entity_id"));
            assertNull(row.get("before"));
        }
        JsonNode refinement = objectMapper.readTree((String) audit.get(0).get("after"));
        assertEquals(Set.of("cycle", "dueDate", "requestedChangeCount"), new HashSet<>(refinement.propertyNames()));
        assertEquals(2, refinement.get("requestedChangeCount").asInt());
        JsonNode mine = objectMapper.readTree((String) audit.get(1).get("after"));
        assertEquals(Set.of("round", "result", "version"), new HashSet<>(mine.propertyNames()));
        assertEquals("NEEDS_MORE_INFORMATION", mine.get("result").asString());
        assertEquals(1, mine.get("round").asInt());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT event_type, payload::text AS payload FROM notification_outbox WHERE occurred_at >= ?",
                Timestamp.from(before));
        assertEquals(1, rows.size());
        assertEquals(REFINEMENT_ACTION, rows.getFirst().get("event_type"));
        assertEquals(OWNER, objectMapper.readTree((String) rows.getFirst().get("payload"))
                .get("notificationEmail").asString());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notifications WHERE created_at >= ?", Integer.class, Timestamp.from(before)));
    }

    // ---- 2: free text ----------------------------------------------------------------------

    @Test
    void theMarkerAppearsInNoAuditRowAndNoActivityFeed() throws Exception {
        String marker = "SECRET-MARKER-xyz";
        UUID brief = briefInFactFinding();
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("titleNote", "Title " + marker);
        draft.put("purposeNote", "Purpose " + marker);
        draft.put("evidenceNote", "Evidence " + marker);
        draft.put("relevantNote", "Relevant " + marker);
        draft.put("missingItems", "Missing " + marker);
        long version = saveDraft(brief, draft);
        Instant before = dbNow();

        needsInformation(brief, body(version, caseVersion(brief), "Guidance " + marker, List.of("Change " + marker)),
                officer()).andExpect(status().isOk());

        // Every column of every audit_log row written by this call.
        List<String> auditRows = jdbcTemplate.queryForList(
                "SELECT row_to_json(a)::text FROM audit_log a WHERE occurred_at >= ?", String.class,
                Timestamp.from(before));
        assertEquals(2, auditRows.size());
        for (String row : auditRows) {
            assertFalse(row.contains(marker), row);
        }
        for (String feed : List.of(
                body(mockMvc.perform(get("/cases/{id}/activity", brief).param("size", "100")
                        .with(user("admin@example.test").roles("ADMINISTRATOR"))).andExpect(status().isOk())),
                body(mockMvc.perform(get("/cases/{id}/activity", brief).param("size", "100")
                        .with(user(OWNER).roles("SUBMITTER"))).andExpect(status().isOk())),
                body(mockMvc.perform(get("/activity").param("entityId", brief.toString()).param("size", "100")
                        .with(user("admin@example.test").roles("ADMINISTRATOR"))).andExpect(status().isOk())))) {
            assertTrue(feed.contains("submission.fact_finding_completed") || feed.contains("Fact-finding"), feed);
            assertFalse(feed.contains(marker), feed);
        }
        // Reported, not asserted against: main's stage-history row (not an audit row) carries the guidance note
        // as its note, as for every ask through CaseFileService.askRefinement.
        assertEquals("Guidance " + marker, jdbcTemplate.queryForObject("""
                SELECT note FROM case_stage_history WHERE submission_id = ? AND to_stage = 'IMPROVEMENT_REQUESTED'
                """, String.class, brief));
    }

    // ---- 3: the submitter's notification ----------------------------------------------------

    @Test
    void theSubmitterNotificationCarriesNoFactFindingOrReviewerText() throws Exception {
        String factFindingMarker = "FF-NOTE-MARKER-abc";
        String reviewMarker = "REVIEW-MARKER-def";
        UUID brief = briefInFactFinding();
        transactions.executeWithoutResult(status -> {
            reviewSettings.saveAndFlush(new ReviewSettings(brief, 1, ReviewMode.PARALLEL, 1, null, "coord@example.test"));
            ReviewAssignment done = new ReviewAssignment(brief, 1, "ni-reviewer@example.test", null, 1,
                    "coord@example.test");
            done.setStatus(ReviewAssignmentStatus.SUBMITTED);
            done = assignments.saveAndFlush(done);
            reviews.saveAndFlush(new Review(done.getId(), ReviewDecision.CHANGES_NEEDED, "Reviewer " + reviewMarker,
                    Instant.parse("2026-11-12T09:00:00Z")));
        });
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("titleNote", "Title " + factFindingMarker);
        draft.put("evidenceNote", "Evidence " + factFindingMarker);
        draft.put("missingItems", "Missing " + factFindingMarker);
        long version = saveDraft(brief, draft);
        LocalDate due = today().plusDays(9);

        Map<String, Object> request = body(version, caseVersion(brief), "Please add a costed budget.",
                List.of("Budget table"));
        request.put("dueDate", due.toString());
        needsInformation(brief, request, officer()).andExpect(status().isOk());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT event_type, payload::text AS payload FROM notification_outbox "
                        + "WHERE aggregate_id = ? AND event_type = ?", brief, REFINEMENT_ACTION);
        assertEquals(1, rows.size());
        String payload = (String) rows.getFirst().get("payload");
        assertEquals(OWNER, objectMapper.readTree(payload).get("notificationEmail").asString());
        assertTrue(payload.contains("Please add a costed budget."), payload);
        for (String hidden : List.of(factFindingMarker, reviewMarker, "ni-reviewer@example.test", "CHANGES_NEEDED")) {
            assertFalse(payload.contains(hidden), hidden + " in " + payload);
        }
    }

    // ---- 4: rejected requests write nothing ---------------------------------------------------

    enum Rejection { FORBIDDEN, NOT_FOUND, CONFLICT, UNPROCESSABLE }

    @ParameterizedTest
    @EnumSource(Rejection.class)
    void aRejectedRequestWritesNothing(Rejection kind) throws Exception {
        UUID brief = briefInFactFinding();
        long version = saveDraft(brief, Map.of("missingItems", "Budget"));
        long caseVersion = caseVersion(brief);
        Map<String, Object> before = state(brief);
        long auditBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log", Long.class);
        long outboxBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox", Long.class);

        switch (kind) {
            case FORBIDDEN -> needsInformation(brief, body(version, caseVersion, "Guidance", List.of("One")),
                    user("coord@example.test").roles("INNOVATION_COORDINATOR")).andExpect(status().isForbidden());
            case NOT_FOUND -> needsInformation(UUID.fromString("00000000-0000-0000-0000-00000000f00d"),
                    body(version, caseVersion, "Guidance", List.of("One")), officer()).andExpect(status().isNotFound());
            case CONFLICT -> needsInformation(brief, body(version + 1, caseVersion, "Guidance", List.of("One")),
                    officer()).andExpect(status().isConflict());
            case UNPROCESSABLE -> needsInformation(brief, body(version, caseVersion, "   ", List.of("One")),
                    officer()).andExpect(status().isUnprocessableContent());
        }

        assertEquals(before, state(brief));
        assertEquals(auditBefore, jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log", Long.class));
        assertEquals(outboxBefore, jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox", Long.class));
    }

    // ---- 5-8: full rollback when a later write fails, then a clean retry ---------------------------

    @Test
    void whenMyAuditFailsAfterTheAskEverythingRollsBackAndARetrySucceeds() throws Exception {
        UUID brief = briefInFactFinding();
        long version = saveDraft(brief, Map.of("missingItems", "Budget"));
        Map<String, Object> request = body(version, caseVersion(brief), "Please add a budget.", List.of("Budget"));
        Map<String, Object> before = state(brief);
        // Interface proxy: pass every other save to the delegating default answer, and fail my entry once.
        Answer<?> real = Mockito.mockingDetails(auditEntries).getMockCreationSettings().getDefaultAnswer();
        AtomicBoolean failed = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            AuditEntry entry = invocation.getArgument(0);
            if (COMPLETED_ACTION.equals(entry.getAction()) && brief.equals(entry.getEntityId())
                    && failed.compareAndSet(false, true)) {
                throw new IllegalStateException("audit store unavailable");
            }
            return real.answer(invocation);
        }).when(auditEntries).save(Mockito.any(AuditEntry.class));

        needsInformation(brief, request, officer()).andExpect(status().isInternalServerError());

        // Main's refinement audit was saved first (askRefinement ran), then mine was attempted and failed.
        Mockito.verify(auditEntries).save(Mockito.argThat(
                (AuditEntry entry) -> entry != null && REFINEMENT_ACTION.equals(entry.getAction())
                        && brief.equals(entry.getEntityId())));
        Mockito.verify(auditEntries).save(Mockito.argThat(
                (AuditEntry entry) -> entry != null && COMPLETED_ACTION.equals(entry.getAction())
                        && brief.equals(entry.getEntityId())));
        assertFullyRolledBack(brief, before, version);

        needsInformation(brief, request, officer()).andExpect(status().isOk());
        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
    }

    @Test
    void whenMainsRefinementInsertFailsEverythingRollsBackAndARetrySucceeds() throws Exception {
        UUID brief = briefInFactFinding();
        long version = saveDraft(brief, Map.of("missingItems", "Budget"));
        Map<String, Object> request = body(version, caseVersion(brief), "Please add a budget.", List.of("Budget"));
        Map<String, Object> before = state(brief);
        Answer<?> real = Mockito.mockingDetails(refinementRequests).getMockCreationSettings().getDefaultAnswer();
        AtomicBoolean failed = new AtomicBoolean();
        Mockito.doAnswer(invocation -> {
            Object entity = invocation.getArgument(0);
            if (entity instanceof RefinementRequest refinement && brief.equals(refinement.getBriefId())
                    && failed.compareAndSet(false, true)) {
                throw new IllegalStateException("refinement store unavailable");
            }
            return real.answer(invocation);
        }).when(refinementRequests).saveAndFlush(ArgumentMatchers.any());

        needsInformation(brief, request, officer()).andExpect(status().isInternalServerError());

        assertTrue(failed.get());
        assertFullyRolledBack(brief, before, version);

        needsInformation(brief, request, officer()).andExpect(status().isOk());
        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
    }

    @Test
    void whenMainsOutboxInsertFailsEverythingRollsBackAndARetrySucceeds() throws Exception {
        UUID brief = briefInFactFinding();
        long version = saveDraft(brief, Map.of("missingItems", "Budget"));
        Map<String, Object> request = body(version, caseVersion(brief), "Please add a budget.", List.of("Budget"));
        Map<String, Object> before = state(brief);
        AtomicBoolean failed = new AtomicBoolean();
        // NotificationOutbox is a concrete class, so the spy can call the real method for every other row.
        Mockito.doAnswer(invocation -> {
            if (brief.equals(invocation.getArgument(0)) && REFINEMENT_ACTION.equals(invocation.getArgument(1))
                    && failed.compareAndSet(false, true)) {
                throw new IllegalStateException("outbox unavailable");
            }
            return invocation.callRealMethod();
        }).when(outbox).recordSubmitterNotification(ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any());

        needsInformation(brief, request, officer()).andExpect(status().isInternalServerError());

        assertTrue(failed.get());
        assertFullyRolledBack(brief, before, version);

        needsInformation(brief, request, officer()).andExpect(status().isOk());
        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox "
                + "WHERE aggregate_id = ? AND event_type = ?", Integer.class, brief, REFINEMENT_ACTION));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void assertFullyRolledBack(UUID brief, Map<String, Object> before, long version) {
        assertEquals(before, state(brief));
        Map<String, Object> check = jdbcTemplate.queryForMap(
                "SELECT result, completed_at, version FROM fact_finding_checks WHERE brief_id = ?", brief);
        assertNull(check.get("result"));
        assertNull(check.get("completed_at"));
        assertEquals(version, ((Number) check.get("version")).longValue());
        assertEquals("IN_FACT_FINDING", stage(brief));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refinement_requests WHERE brief_id = ?", Integer.class, brief));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM case_stage_history WHERE submission_id = ? "
                + "AND to_stage = 'IMPROVEMENT_REQUESTED'", Integer.class, brief));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ? "
                + "AND action IN (?, ?)", Integer.class, brief, REFINEMENT_ACTION, COMPLETED_ACTION));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM notification_outbox "
                + "WHERE aggregate_id = ? AND event_type = ?", Integer.class, brief, REFINEMENT_ACTION));
    }

    /** The check, the stage, the refinement table and every row this brief owns in the written tables. */
    private Map<String, Object> state(UUID brief) {
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT stage FROM submissions WHERE id = ?) AS stage,
                       (SELECT version FROM submissions WHERE id = ?) AS case_version,
                       (SELECT result FROM fact_finding_checks WHERE brief_id = ?) AS result,
                       (SELECT completed_at FROM fact_finding_checks WHERE brief_id = ?) AS completed_at,
                       (SELECT version FROM fact_finding_checks WHERE brief_id = ?) AS check_version,
                       (SELECT count(*) FROM refinement_requests WHERE brief_id = ?) AS requests,
                       (SELECT count(*) FROM case_stage_history WHERE submission_id = ?) AS history,
                       (SELECT count(*) FROM audit_log WHERE entity_id = ?) AS audit,
                       (SELECT count(*) FROM notification_outbox WHERE aggregate_id = ?) AS outbox
                """, brief, brief, brief, brief, brief, brief, brief, brief, brief);
    }

    private Instant dbNow() {
        return jdbcTemplate.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
    }

    private ResultActions needsInformation(UUID brief, Map<String, ?> body, RequestPostProcessor caller)
            throws Exception {
        return mockMvc.perform(post(PATH, brief).with(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> body(long version, long caseVersion, String guidance, List<String> changes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("caseVersion", caseVersion);
        body.put("guidanceNote", guidance);
        body.put("dueDate", today().plusDays(7).toString());
        body.put("requestedChanges", changes);
        return body;
    }

    private long saveDraft(UUID brief, Map<String, Object> answers) throws Exception {
        return objectMapper.readTree(body(mockMvc.perform(put("/cases/{id}/fact-finding", brief).with(officer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers)))
                .andExpect(status().isOk()))).get("version").asLong();
    }

    private long caseVersion(UUID brief) throws Exception {
        return objectMapper.readTree(body(mockMvc.perform(get("/cases/{id}", brief)
                        .with(user("coord@example.test").roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk()))).get("submission").get("version").asLong();
    }

    private String stage(UUID brief) {
        return jdbcTemplate.queryForObject("SELECT stage FROM submissions WHERE id = ?", String.class, brief);
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    /** A committed IN_FACT_FINDING brief owned by OWNER (with email), forwarded to a new sector. */
    private UUID briefInFactFinding() {
        UUID id = transactions.execute(status -> {
            Sector sector = sectors.saveAndFlush(new Sector(UUID.randomUUID(), "Sector " + UUID.randomUUID(),
                    "sector-" + UUID.randomUUID() + "@example.test", true, Instant.parse("2026-11-01T08:00:00Z"),
                    Instant.parse("2026-11-01T08:00:00Z")));
            sectorIds.add(sector.getId());
            Submission brief = new Submission(UUID.randomUUID(), SubmissionCategory.GENERAL, "NI audit brief",
                    "Purpose", "Problem", "receptionist@example.test", OWNER, null, null, null, null, OWNER, "Owner",
                    Instant.parse("2026-11-01T08:00:00Z"));
            brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
            brief.register("CASE-NA-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            brief.forward(sector, Instant.parse("2026-11-01T10:00:00Z"));
            brief.transitionTo(SubmissionStage.IN_FACT_FINDING, Instant.parse("2026-11-01T11:00:00Z"));
            return submissions.saveAndFlush(brief).getId();
        });
        briefs.add(id);
        return id;
    }

    private static LocalDate today() {
        return LocalDate.now(HUB_ZONE);
    }

    private static RequestPostProcessor officer() {
        return user(OFFICER).roles("FACT_FINDING_OFFICER");
    }
}
