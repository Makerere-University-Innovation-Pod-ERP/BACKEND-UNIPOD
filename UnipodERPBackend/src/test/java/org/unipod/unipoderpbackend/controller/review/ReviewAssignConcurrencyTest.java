package org.unipod.unipoderpbackend.controller.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.transaction.support.TransactionTemplate;
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

/**
 * Real concurrent requests with committed data (no test transaction): the submission row lock
 * must serialise assignments so that no request fails with 500 and positions never collide.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewAssignConcurrencyTest {
    private static final String COORDINATOR = "coord@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ReviewSettingsRepository settings;

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
            jdbcTemplate.update("DELETE FROM review_assignments WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM review_settings WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        for (String email : emails) {
            jdbcTemplate.update("DELETE FROM api_users WHERE email = ?", email);
        }
    }

    @RepeatedTest(3)
    void concurrentAssignmentsOfDifferentReviewersGetDistinctPositions() throws Exception {
        UUID brief = configuredBrief();
        String first = reviewer();
        String second = reviewer();

        List<Integer> statuses = concurrently(assignCall(brief, first), assignCall(brief, second));

        assertEquals(List.of(201, 201), statuses);
        assertEquals(List.of(1, 2), jdbcTemplate.queryForList(
                "SELECT position FROM review_assignments WHERE brief_id = ? ORDER BY position",
                Integer.class, brief));
    }

    @RepeatedTest(3)
    void concurrentAssignmentsOfTheSameReviewerGiveOneCreatedAndOneConflict() throws Exception {
        UUID brief = configuredBrief();
        String reviewer = reviewer();

        List<Integer> statuses = concurrently(assignCall(brief, reviewer), assignCall(brief, reviewer));

        assertEquals(List.of(201, 409), statuses.stream().sorted().toList());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM review_assignments WHERE brief_id = ?", Integer.class, brief));
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
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> assignCall(UUID brief, String reviewer) {
        return () -> mockMvc.perform(post("/cases/{id}/reviewers", brief)
                        .with(user(COORDINATOR).roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerId\":\"" + reviewer + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private String reviewer() {
        String email = "concurrent-" + UUID.randomUUID() + "@example.test";
        transactions.executeWithoutResult(status -> users.save(new ApiUser(
                UUID.randomUUID(), email, "not-a-real-hash", ApiUserRole.REVIEWER, Instant.now())));
        emails.add(email);
        return email;
    }

    private UUID configuredBrief() {
        UUID id = transactions.execute(status -> {
            Submission brief = new Submission(
                    UUID.randomUUID(),
                    SubmissionCategory.GENERAL,
                    "Concurrent brief",
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
            brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
            brief.register("CASE-CC-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            Instant time = Instant.parse("2026-11-01T10:00:00Z");
            brief.transitionTo(SubmissionStage.FORWARDED, time);
            brief.transitionTo(SubmissionStage.IN_FACT_FINDING, time);
            brief.transitionTo(SubmissionStage.IN_REVIEW, time);
            Submission saved = submissions.save(brief);
            settings.save(new ReviewSettings(saved.getId(), 1, ReviewMode.PARALLEL, 2, null, COORDINATOR));
            return saved.getId();
        });
        briefs.add(id);
        return id;
    }
}
