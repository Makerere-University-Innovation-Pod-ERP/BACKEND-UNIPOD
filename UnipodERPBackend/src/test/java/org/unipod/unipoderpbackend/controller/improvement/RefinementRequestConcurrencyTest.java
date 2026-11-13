package org.unipod.unipoderpbackend.controller.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Two simultaneous asks on the same brief: the brief row lock serialises them, so the winner moves the
 * brief and creates cycle 1, and the loser then finds an open request and gets 409 (never 500).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefinementRequestConcurrencyTest {
    private static final String COORDINATOR = "race-improvement-coord@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> briefs = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            jdbcTemplate.update("""
                    DELETE FROM refinement_requested_changes WHERE request_id IN
                        (SELECT id FROM refinement_requests WHERE brief_id = ?)
                    """, brief);
            jdbcTemplate.update("DELETE FROM refinement_requests WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM notification_outbox WHERE aggregate_id = ?", brief);
            jdbcTemplate.update("DELETE FROM case_stage_history WHERE submission_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        briefs.clear();
    }

    @RepeatedTest(3)
    void twoSimultaneousAsksGiveOneCreatedAndOneConflict() throws Exception {
        UUID brief = transactions.execute(status ->
                submissions.save(ImprovementTestBriefs.inStage(SubmissionStage.IN_REVIEW, "CASE-IR-")).getId());
        briefs.add(brief);
        long version = objectMapper.readTree(mockMvc.perform(get("/cases/{id}", brief)
                        .with(user(COORDINATOR).roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("submission").get("version").asLong();
        String body = objectMapper.writeValueAsString(RefinementRequestApiIntegrationTest.body("Race.",
                LocalDate.now(ZoneId.of("Africa/Kampala")).plusDays(3), List.of("One"), version));
        Callable<Integer> ask = () -> mockMvc.perform(post("/cases/{id}/refinement-requests", brief)
                        .with(user(COORDINATOR).roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();

        assertEquals(List.of(201, 409), concurrently(ask, ask));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refinement_requests WHERE brief_id = ?", Integer.class, brief));
        assertEquals("IMPROVEMENT_REQUESTED", jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?", String.class, brief));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'submission.improvement_requested'",
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
