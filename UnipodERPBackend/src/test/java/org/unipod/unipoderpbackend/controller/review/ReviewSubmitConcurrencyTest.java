package org.unipod.unipoderpbackend.controller.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * Real concurrent submits with committed data (no test transaction): the submission row lock
 * serialises them, so the same reviewer gets exactly one 201 and one 409, never a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewSubmitConcurrencyTest {
    private static final String COORDINATOR = "coord@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    private final List<UUID> briefs = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            jdbcTemplate.update("""
                    DELETE FROM reviews WHERE assignment_id IN
                        (SELECT id FROM review_assignments WHERE brief_id = ?)
                    """, brief);
            jdbcTemplate.update("DELETE FROM review_assignments WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM review_settings WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        briefs.clear();
    }

    @RepeatedTest(3)
    void concurrentSubmitsBySameReviewerGiveOneCreatedAndOneConflict() throws Exception {
        String reviewer = "concurrent-" + UUID.randomUUID() + "@example.test";
        UUID brief = briefWithAssignment(reviewer);

        List<MvcResult> results = concurrently(submitCall(brief, reviewer), submitCall(brief, reviewer));

        List<Integer> statuses = results.stream().map(result -> result.getResponse().getStatus()).sorted().toList();
        assertEquals(List.of(201, 409), statuses);
        MvcResult conflict = results.stream()
                .filter(result -> result.getResponse().getStatus() == 409)
                .findFirst()
                .orElseThrow();
        assertTrue(conflict.getResponse().getContentAsString()
                .contains("You have already submitted your review for this brief."));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ?
                """, Integer.class, brief));
        assertEquals("SUBMITTED", jdbcTemplate.queryForObject(
                "SELECT status FROM review_assignments WHERE brief_id = ?", String.class, brief));
    }

    private List<MvcResult> concurrently(Callable<MvcResult> one, Callable<MvcResult> two) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : List.of(one, two)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<MvcResult> submitCall(UUID brief, String reviewer) {
        return () -> mockMvc.perform(post("/cases/{id}/reviews", brief)
                        .with(user(reviewer).roles("REVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RECOMMENDED\",\"comment\":\"Race.\",\"assignmentVersion\":0}"))
                .andReturn();
    }

    private UUID briefWithAssignment(String reviewer) {
        UUID id = transactions.execute(status -> {
            Submission brief = new Submission(
                    UUID.randomUUID(),
                    SubmissionCategory.GENERAL,
                    "Concurrent review brief",
                    "Purpose",
                    "Problem",
                    "receptionist@example.test",
                    "submitter@example.test",
                    null,
                    null,
                    null,
                    null,
                    Instant.parse("2026-11-01T08:00:00Z")
            );
            brief.register("CASE-RX-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            Instant time = Instant.parse("2026-11-01T10:00:00Z");
            brief.transitionTo(SubmissionStage.REGISTERED, time);
            brief.transitionTo(SubmissionStage.FACT_FINDING, time);
            brief.transitionTo(SubmissionStage.UNDER_REVIEW, time);
            Submission saved = submissions.save(brief);
            settings.save(new ReviewSettings(saved.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
            assignments.save(new ReviewAssignment(saved.getId(), 1, reviewer, null, 1, COORDINATOR));
            return saved.getId();
        });
        briefs.add(id);
        return id;
    }
}
