package org.unipod.unipoderpbackend.controller.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Committed-data tests for accepting a revision (no surrounding test transaction): a failure in my audit after
 * Waluube's transition rolls the stage change back, and two simultaneous accepts give one 200 and one 409.
 * The audit spy needs its own Spring context, closed after this class to free its connection pool.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class RevisionAcceptanceCommittedTest {
    private static final String COORDINATOR = "committed-accept-coord@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private FactFindingRepository factFinding;

    @Autowired
    private RefinementRequestRepository requests;

    @Autowired
    private CaseRevisionRepository revisions;

    @MockitoSpyBean
    private AuditEntryRepository auditEntries;

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
            jdbcTemplate.update("DELETE FROM case_revisions WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM refinement_requests WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM fact_finding_checks WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM notification_outbox WHERE aggregate_id = ?", brief);
            jdbcTemplate.update("DELETE FROM case_stage_history WHERE submission_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        briefs.clear();
    }

    @Test
    void failureInMyAuditRollsBackTheTransition() throws Exception {
        Seeded seeded = resubmitted();
        Map<String, Object> before = state(seeded.brief());
        // A repository spy is an interface proxy: pass other saves to its delegating default answer
        // (callRealMethod is not possible on it), and fail only my own audit entry.
        Answer<?> real = Mockito.mockingDetails(auditEntries).getMockCreationSettings().getDefaultAnswer();
        Mockito.doAnswer(invocation -> {
            AuditEntry entry = invocation.getArgument(0);
            if ("submission.improved_version_accepted".equals(entry.getAction())) {
                throw new IllegalStateException("audit store unavailable");
            }
            return real.answer(invocation);
        }).when(auditEntries).save(Mockito.any(AuditEntry.class));

        assertEquals(500, acceptCall(seeded).call());

        // The transition's own audit entry was saved first, then mine was reached and failed.
        Mockito.verify(auditEntries).save(Mockito.argThat(
                (AuditEntry entry) -> entry != null && "submission.transitioned".equals(entry.getAction())));
        Mockito.verify(auditEntries).save(Mockito.argThat(
                (AuditEntry entry) -> entry != null && "submission.improved_version_accepted".equals(entry.getAction())));
        assertEquals(before, state(seeded.brief()));
        assertEquals("RESUBMITTED", before.get("stage"));
    }

    @RepeatedTest(3)
    void twoSimultaneousAcceptsGiveOneOkAndOneConflict() throws Exception {
        Seeded seeded = resubmitted();

        assertEquals(List.of(200, 409), concurrently(acceptCall(seeded), acceptCall(seeded)));
        assertEquals("IN_REVIEW", jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?", String.class, seeded.brief()));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM case_stage_history
                WHERE submission_id = ? AND from_stage = 'RESUBMITTED' AND to_stage = 'IN_REVIEW'
                """, Integer.class, seeded.brief()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'submission.improved_version_accepted'",
                Integer.class, seeded.brief()));
    }

    private Callable<Integer> acceptCall(Seeded seeded) throws Exception {
        long version = objectMapper.readTree(mockMvc.perform(get("/cases/{id}", seeded.brief())
                        .with(user(COORDINATOR).roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("submission").get("version").asLong();
        return () -> mockMvc.perform(post("/cases/{id}/revisions/{revisionId}/accept", seeded.brief(), seeded.revision())
                        .with(user(COORDINATOR).roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseVersion\":" + version + "}"))
                .andReturn().getResponse().getStatus();
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

    private Map<String, Object> state(UUID brief) {
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT stage FROM submissions WHERE id = ?) AS stage,
                       (SELECT version FROM submissions WHERE id = ?) AS version,
                       (SELECT count(*) FROM case_stage_history WHERE submission_id = ?) AS history,
                       (SELECT count(*) FROM audit_log WHERE entity_id = ?) AS audit
                """, brief, brief, brief, brief);
    }

    record Seeded(UUID brief, long revision) {
    }

    /** RESUBMITTED brief with a completed fact-finding check, an answered request and revision 2 (no audit rows). */
    private Seeded resubmitted() {
        Seeded seeded = transactions.execute(status -> {
            UUID brief = submissions.save(ImprovementTestBriefs.ownedBy(
                    "committed-owner@example.test", SubmissionStage.RESUBMITTED, "CASE-AT-")).getId();
            FactFindingCheck check = new FactFindingCheck(brief, 1, "officer@example.test");
            check.complete(FactFindingResult.READY_FOR_REVIEW, Instant.parse("2026-11-10T09:00:00Z"));
            factFinding.save(check);
            RefinementRequest request = new RefinementRequest(brief, 1, RefinementSource.COORDINATOR, "Improve.",
                    LocalDate.of(2027, 1, 1), COORDINATOR, Instant.parse("2026-11-12T09:00:00Z"));
            request.setStatus(RefinementStatus.RESPONDED);
            request.setRespondedAt(Instant.parse("2026-11-13T09:00:00Z"));
            RefinementRequest saved = requests.saveAndFlush(request);
            CaseRevision revision = revisions.save(new CaseRevision(brief, 2, saved.getId(), "Changed.",
                    "committed-owner@example.test", Instant.parse("2026-11-13T09:00:00Z")));
            return new Seeded(brief, revision.getId());
        });
        briefs.add(seeded.brief());
        return seeded;
    }
}
