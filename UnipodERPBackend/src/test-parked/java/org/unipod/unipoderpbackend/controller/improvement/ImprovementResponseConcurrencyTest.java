package org.unipod.unipoderpbackend.controller.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.time.LocalDate;
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
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * Two simultaneous answers to the same request: the brief row lock serialises them, so the winner answers
 * and the loser then finds the brief RESUBMITTED and gets 409 (never 500), with exactly one revision.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImprovementResponseConcurrencyTest {
    private static final String OWNER = "race-answer-owner@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private RefinementRequestRepository requests;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    private final List<UUID> briefs = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            jdbcTemplate.update("DELETE FROM case_revisions WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM refinement_requests WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM notification_outbox WHERE aggregate_id = ?", brief);
            jdbcTemplate.update("DELETE FROM case_stage_history WHERE submission_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        briefs.clear();
    }

    @RepeatedTest(3)
    void twoSimultaneousAnswersGiveOneCreatedAndOneConflict() throws Exception {
        UUID brief = transactions.execute(status -> {
            UUID id = submissions.save(ImprovementTestBriefs.ownedBy(
                    OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-AR-")).getId();
            requests.save(new RefinementRequest(id, 1, RefinementSource.COORDINATOR, "Please improve.",
                    LocalDate.of(2027, 1, 1), "coord@example.test", Instant.parse("2026-11-12T09:00:00Z")));
            return id;
        });
        briefs.add(brief);
        Callable<Integer> answer = () -> mockMvc.perform(post("/cases/{id}/improved-version", brief)
                        .with(user(OWNER).roles("SUBMITTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatChanged\":\"Race.\",\"requestVersion\":0}"))
                .andReturn().getResponse().getStatus();

        assertEquals(List.of(201, 409), concurrently(answer, answer));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM case_revisions WHERE brief_id = ?", Integer.class, brief));
        assertEquals("RESUBMITTED", jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?", String.class, brief));
        assertEquals("RESPONDED", jdbcTemplate.queryForObject(
                "SELECT status FROM refinement_requests WHERE brief_id = ?", String.class, brief));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'submission.improved_version_submitted'",
                Integer.class, brief));
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
}
