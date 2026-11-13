package org.unipod.unipoderpbackend.service.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs without a surrounding test transaction, so each request commits or rolls back for real.
 * The audit row and the data change always share one transaction: a failure after the audit call
 * removes both, and a failing audit insert rolls back the data change.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewAuditAtomicityTest {
    private static final String OFFICER = "atomic-officer@example.test";
    private static final String COORDINATOR = "atomic-coord@example.test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private SubmissionRepository submissions;

    @MockitoSpyBean
    private AuditService auditService;

    @MockitoSpyBean
    private AuditEntryRepository auditEntries;

    @MockitoSpyBean
    private ReviewPanelQueryService panels;

    @MockitoSpyBean
    private ReviewQuorumService quorum;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ApiUserRepository users;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> briefs = new ArrayList<>();
    private final List<String> emails = new ArrayList<>();

    // Spies are reset by @MockitoSpyBean after each test; only committed data is cleaned here.
    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            jdbcTemplate.update("""
                    DELETE FROM reviews WHERE assignment_id IN
                        (SELECT id FROM review_assignments WHERE brief_id = ?)
                    """, brief);
            jdbcTemplate.update("DELETE FROM review_assignments WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM review_settings WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM fact_finding_checks WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM case_stage_history WHERE submission_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        for (String email : emails) {
            jdbcTemplate.update("DELETE FROM api_users WHERE email = ?", email);
        }
        briefs.clear();
        emails.clear();
    }

    // ---- 5: a failure after the audit call removes the audit row and the data change ------

    @Test
    void failureAfterTheAuditCallRollsBackFactFindingCompletion() throws Exception {
        UUID brief = brief(SubmissionStage.FACT_FINDING);
        long version = savedVersion(brief);
        Mockito.clearInvocations(auditService);
        Mockito.doThrow(new IllegalStateException("flush failed")).when(submissions).flush();

        expectServerError(perform(post("/cases/{id}/fact-finding/complete", brief), Map.of("version", version), officer()));

        Mockito.verify(auditService).log(Mockito.any(AuditEvent.class));
        assertEquals(0, rows(brief, "submission.fact_finding_completed"));
        Map<String, Object> check = jdbcTemplate.queryForMap(
                "SELECT completed_at, result, version FROM fact_finding_checks WHERE brief_id = ?", brief);
        assertNull(check.get("completed_at"));
        assertNull(check.get("result"));
        assertEquals(version, ((Number) check.get("version")).longValue());
        assertEquals("FACT_FINDING", stage(brief));
    }

    @Test
    void failureAfterTheAuditCallRollsBackSettingsAssignAndRemove() throws Exception {
        UUID brief = brief(SubmissionStage.UNDER_REVIEW);
        String reviewer = reviewerAccount();
        Mockito.doThrow(new IllegalStateException("panel failed")).when(panels).panelFor(Mockito.any(UUID.class));

        expectServerError(perform(put("/cases/{id}/review-settings", brief), Map.of("mode", "PARALLEL", "reviewsNeeded", 1),
                coordinator()));
        assertEquals(0, count("SELECT count(*) FROM review_settings WHERE brief_id = ?", brief));
        assertEquals(0, rows(brief, ReviewAudit.SETTINGS_SAVED));

        seedSettings(brief);
        expectServerError(perform(post("/cases/{id}/reviewers", brief), Map.of("reviewerId", reviewer), coordinator()));
        assertEquals(0, count("SELECT count(*) FROM review_assignments WHERE brief_id = ?", brief));
        assertEquals(0, rows(brief, ReviewAudit.REVIEWER_ASSIGNED));

        seedAssignment(brief, reviewer, 1);
        expectServerError(mockMvc.perform(delete("/cases/{id}/reviewers/{reviewerId}", brief, reviewer).with(coordinator())));
        assertEquals(1, count("SELECT count(*) FROM review_assignments WHERE brief_id = ?", brief));
        assertEquals(0, rows(brief, ReviewAudit.REVIEWER_REMOVED));

        Mockito.verify(auditService, Mockito.times(3)).log(Mockito.any(AuditEvent.class));
    }

    @Test
    void failureAfterTheAuditCallRollsBackReviewSubmission() throws Exception {
        UUID brief = brief(SubmissionStage.UNDER_REVIEW);
        String reviewer = reviewerAccount();
        seedSettings(brief);
        seedAssignment(brief, reviewer, 1);
        Mockito.doThrow(new IllegalStateException("quorum failed")).when(quorum).quorumFor(Mockito.any(ReviewSettings.class));

        expectServerError(perform(post("/cases/{id}/reviews", brief),
                Map.of("decision", "RECOMMENDED", "comment", "Rolled back.", "assignmentVersion", 0),
                user(reviewer).roles("REVIEWER")));

        Mockito.verify(auditService).log(Mockito.any(AuditEvent.class));
        assertEquals(0, rows(brief, ReviewAudit.REVIEW_SUBMITTED));
        assertEquals(0, count("""
                SELECT count(*) FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ?
                """, brief));
        assertEquals("NOT_STARTED", jdbcTemplate.queryForObject(
                "SELECT status FROM review_assignments WHERE brief_id = ?", String.class, brief));
    }

    // ---- 6: a failing audit insert rolls back the data change ----------------------------

    @Test
    void failingAuditRollsBackEveryOneOfTheSixWrites() throws Exception {
        UUID factFinding = brief(SubmissionStage.FACT_FINDING);
        UUID toComplete = brief(SubmissionStage.FACT_FINDING);
        long version = savedVersion(toComplete);
        UUID underReview = brief(SubmissionStage.UNDER_REVIEW);
        UUID withSettings = brief(SubmissionStage.UNDER_REVIEW);
        String reviewer = reviewerAccount();
        String assigned = reviewerAccount();
        seedSettings(withSettings);
        seedAssignment(withSettings, assigned, 1);
        // AuditService.log itself fails: its insert into audit_log throws inside the caller's transaction.
        Mockito.doThrow(new IllegalStateException("audit store unavailable"))
                .when(auditEntries).save(Mockito.any(AuditEntry.class));

        expectServerError(perform(put("/cases/{id}/fact-finding", factFinding),
                Map.of("titleClear", true, "titleNote", "note"), officer()));
        assertEquals(0, count("SELECT count(*) FROM fact_finding_checks WHERE brief_id = ?", factFinding));

        expectServerError(perform(post("/cases/{id}/fact-finding/complete", toComplete), Map.of("version", version),
                officer()));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT completed_at FROM fact_finding_checks WHERE brief_id = ?", Instant.class, toComplete));
        assertEquals("FACT_FINDING", stage(toComplete));
        assertEquals(0, count("SELECT count(*) FROM case_stage_history WHERE submission_id = ?", toComplete));

        expectServerError(perform(put("/cases/{id}/review-settings", underReview),
                Map.of("mode", "PARALLEL", "reviewsNeeded", 1), coordinator()));
        assertEquals(0, count("SELECT count(*) FROM review_settings WHERE brief_id = ?", underReview));

        expectServerError(perform(post("/cases/{id}/reviewers", withSettings), Map.of("reviewerId", reviewer),
                coordinator()));
        assertEquals(1, count("SELECT count(*) FROM review_assignments WHERE brief_id = ?", withSettings));

        expectServerError(mockMvc.perform(delete("/cases/{id}/reviewers/{reviewerId}", withSettings, assigned)
                .with(coordinator())));
        assertEquals(1, count("SELECT count(*) FROM review_assignments WHERE brief_id = ?", withSettings));

        expectServerError(perform(post("/cases/{id}/reviews", withSettings),
                Map.of("decision", "RECOMMENDED", "comment", "Rolled back.", "assignmentVersion", 0),
                user(assigned).roles("REVIEWER")));
        assertEquals(0, count("""
                SELECT count(*) FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ?
                """, withSettings));
        assertEquals("NOT_STARTED", jdbcTemplate.queryForObject(
                "SELECT status FROM review_assignments WHERE brief_id = ?", String.class, withSettings));

        for (UUID brief : List.of(factFinding, underReview, withSettings)) {
            assertEquals(0, count("SELECT count(*) FROM audit_log WHERE entity_id = ?", brief));
        }
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE entity_id = ?", toComplete)); // the setup save
    }

    // ---- helpers -------------------------------------------------------------------------

    private ResultActions perform(MockHttpServletRequestBuilder request, Map<String, Object> body,
                                  RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(request.with(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static void expectServerError(ResultActions result) throws Exception {
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.reference").isNotEmpty());
    }

    private long savedVersion(UUID brief) throws Exception {
        String body = perform(put("/cases/{id}/fact-finding", brief), Map.of(
                "titleClear", true, "purposeClear", true, "evidenceEnough", true, "relevantToHub", true), officer())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("version").asLong();
    }

    private int rows(UUID brief, String action) {
        return count("SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = '" + action + "'", brief);
    }

    private int count(String sql, UUID brief) {
        return jdbcTemplate.queryForObject(sql, Integer.class, brief);
    }

    private String stage(UUID brief) {
        return jdbcTemplate.queryForObject("SELECT stage FROM submissions WHERE id = ?", String.class, brief);
    }

    private static RequestPostProcessor officer() {
        return user(OFFICER).roles("FACT_FINDING_OFFICER");
    }

    private static RequestPostProcessor coordinator() {
        return user(COORDINATOR).roles("INNOVATION_COORDINATOR");
    }

    private String reviewerAccount() {
        String email = "atomic-" + UUID.randomUUID() + "@example.test";
        transactions.executeWithoutResult(status -> users.save(new ApiUser(
                UUID.randomUUID(), email, "not-a-real-hash", ApiUserRole.REVIEWER, Instant.now())));
        emails.add(email);
        return email;
    }

    private void seedSettings(UUID brief) {
        transactions.executeWithoutResult(status ->
                settings.save(new ReviewSettings(brief, 1, ReviewMode.PARALLEL, 1, null, COORDINATOR)));
    }

    private void seedAssignment(UUID brief, String reviewer, int position) {
        transactions.executeWithoutResult(status ->
                assignments.save(new ReviewAssignment(brief, 1, reviewer, null, position, COORDINATOR)));
    }

    private UUID brief(SubmissionStage target) {
        UUID id = transactions.execute(status -> {
            Submission brief = new Submission(
                    UUID.randomUUID(), SubmissionCategory.GENERAL, "Atomic audit brief", "Purpose", "Problem",
                    "receptionist@example.test", "submitter@example.test", null, null, null, null,
                    Instant.parse("2026-11-01T08:00:00Z"));
            brief.register("CASE-AA-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            Instant time = Instant.parse("2026-11-01T10:00:00Z");
            for (SubmissionStage stage : List.of(
                    SubmissionStage.REGISTERED, SubmissionStage.FACT_FINDING, SubmissionStage.UNDER_REVIEW)) {
                brief.transitionTo(stage, time);
                if (stage == target) {
                    break;
                }
            }
            return submissions.save(brief).getId();
        });
        briefs.add(id);
        return id;
    }
}
