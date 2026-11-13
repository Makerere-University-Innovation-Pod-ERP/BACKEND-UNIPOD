package org.unipod.unipoderpbackend.controller.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * Real concurrent requests with committed data: the winner writes exactly one audit row and the
 * loser (rejected with 409 or 422) writes none.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewAuditConcurrencyTest {
    private static final String COORDINATOR = "concurrent-coord@example.test";
    private static final String OFFICER = "concurrent-officer@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

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

    private final List<UUID> briefs = new ArrayList<>();
    private final List<String> emails = new ArrayList<>();

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
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        for (String email : emails) {
            jdbcTemplate.update("DELETE FROM api_users WHERE email = ?", email);
        }
        briefs.clear();
        emails.clear();
    }

    @RepeatedTest(3)
    void concurrentFirstDraftsGiveOneSuccessAndOneAuditRow() throws Exception {
        UUID brief = brief(SubmissionStage.FACT_FINDING, false);
        Callable<Integer> save = () -> mockMvc.perform(put("/cases/{id}/fact-finding", brief)
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titleClear\":true}"))
                .andReturn().getResponse().getStatus();

        assertEquals(List.of(200, 422), concurrently(save, save));
        assertEquals(1, auditRows(brief, "submission.fact_finding_saved"));
        assertEquals(1, auditRows(brief, null));
    }

    @RepeatedTest(3)
    void concurrentAssignmentsOfTheSameReviewerGiveOneSuccessAndOneAuditRow() throws Exception {
        UUID brief = brief(SubmissionStage.UNDER_REVIEW, true);
        String reviewer = reviewer();
        Callable<Integer> assign = () -> mockMvc.perform(post("/cases/{id}/reviewers", brief)
                        .with(user(COORDINATOR).roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerId\":\"" + reviewer + "\"}"))
                .andReturn().getResponse().getStatus();

        assertEquals(List.of(201, 409), concurrently(assign, assign));
        assertEquals(1, auditRows(brief, "submission.reviewer_assigned"));
        assertEquals(1, auditRows(brief, null));
    }

    @RepeatedTest(3)
    void concurrentSubmitsBySameReviewerGiveOneSuccessAndOneAuditRow() throws Exception {
        UUID brief = brief(SubmissionStage.UNDER_REVIEW, true);
        String reviewer = reviewer();
        transactions.executeWithoutResult(status ->
                assignments.save(new ReviewAssignment(brief, 1, reviewer, null, 1, COORDINATOR)));
        Callable<Integer> submit = () -> mockMvc.perform(post("/cases/{id}/reviews", brief)
                        .with(user(reviewer).roles("REVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RECOMMENDED\",\"comment\":\"Race.\",\"assignmentVersion\":0}"))
                .andReturn().getResponse().getStatus();

        assertEquals(List.of(201, 409), concurrently(submit, submit));
        assertEquals(1, auditRows(brief, "submission.review_submitted"));
        assertEquals(1, auditRows(brief, null));
    }

    private int auditRows(UUID brief, String action) {
        return action == null
                ? jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ?", Integer.class, brief)
                : jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = ?",
                        Integer.class, brief, action);
    }

    private List<Integer> concurrently(Callable<Integer> one, Callable<Integer> two) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : List.of(one, two)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses.stream().sorted().toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private String reviewer() {
        String email = "audit-race-" + UUID.randomUUID() + "@example.test";
        transactions.executeWithoutResult(status -> users.save(new ApiUser(
                UUID.randomUUID(), email, "not-a-real-hash", ApiUserRole.REVIEWER, Instant.now())));
        emails.add(email);
        return email;
    }

    private UUID brief(SubmissionStage target, boolean withSettings) {
        UUID id = transactions.execute(status -> {
            Submission brief = new Submission(
                    UUID.randomUUID(), SubmissionCategory.GENERAL, "Audit race brief", "Purpose", "Problem",
                    "receptionist@example.test", "submitter@example.test", null, null, null, null,
                    Instant.parse("2026-11-01T08:00:00Z"));
            brief.register("CASE-AR-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            Instant time = Instant.parse("2026-11-01T10:00:00Z");
            for (SubmissionStage stage : List.of(
                    SubmissionStage.REGISTERED, SubmissionStage.FACT_FINDING, SubmissionStage.UNDER_REVIEW)) {
                brief.transitionTo(stage, time);
                if (stage == target) {
                    break;
                }
            }
            Submission saved = submissions.save(brief);
            if (withSettings) {
                settings.save(new ReviewSettings(saved.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
            }
            return saved.getId();
        });
        briefs.add(id);
        return id;
    }
}
