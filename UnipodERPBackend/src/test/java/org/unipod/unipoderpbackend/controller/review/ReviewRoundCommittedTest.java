package org.unipod.unipoderpbackend.controller.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * Committed-data tests for starting a review round (no surrounding test transaction): a failure in my audit
 * after the settings save leaves no round-2 row, and two simultaneous starts give one 201 and one 409.
 * The audit spy needs its own Spring context, closed after this class to free its connection pool.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ReviewRoundCommittedTest {
    private static final String COORDINATOR = "committed-round-coord@example.test";
    private static final String OWNER = "committed-round-owner@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private FactFindingRepository factFinding;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ReviewRepository reviews;

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

    private final List<UUID> briefs = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            // audit_log is append-only (a trigger rejects DELETE); its rows stay, keyed by this random brief id.
            jdbcTemplate.update("DELETE FROM reviews WHERE assignment_id IN "
                    + "(SELECT id FROM review_assignments WHERE brief_id = ?)", brief);
            jdbcTemplate.update("DELETE FROM review_assignments WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM review_settings WHERE brief_id = ?", brief);
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
    void failureInMyAuditLeavesNoNewRound() throws Exception {
        UUID brief = cameBackFromImprovement();
        // A repository spy is an interface proxy: pass other saves to its delegating default answer
        // (callRealMethod is not possible on it), and fail only my own audit entry.
        Answer<?> real = Mockito.mockingDetails(auditEntries).getMockCreationSettings().getDefaultAnswer();
        Mockito.doAnswer(invocation -> {
            AuditEntry entry = invocation.getArgument(0);
            if ("submission.review_round_started".equals(entry.getAction())) {
                throw new IllegalStateException("audit store unavailable");
            }
            return real.answer(invocation);
        }).when(auditEntries).save(Mockito.any(AuditEntry.class));

        assertEquals(500, startCall(brief).call());

        // My audit entry was reached (after the settings save and flush) and failed.
        Mockito.verify(auditEntries).save(Mockito.argThat(
                (AuditEntry entry) -> entry != null && "submission.review_round_started".equals(entry.getAction())));
        assertEquals(Map.of("rounds", 1L, "round2", 0L, "audit", 0L), state(brief));
    }

    @RepeatedTest(3)
    void twoSimultaneousStartsGiveOneCreatedAndOneConflict() throws Exception {
        UUID brief = cameBackFromImprovement();

        assertEquals(List.of(201, 409), concurrently(startCall(brief), startCall(brief)));
        assertEquals(Map.of("rounds", 2L, "round2", 1L, "audit", 1L), state(brief));
    }

    private Callable<Integer> startCall(UUID brief) {
        return () -> mockMvc.perform(post("/cases/{id}/review-rounds", brief)
                        .with(user(COORDINATOR).roles("INNOVATION_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"PARALLEL\",\"reviewsNeeded\":1,\"settingsVersion\":0}"))
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
                SELECT (SELECT count(*) FROM review_settings WHERE brief_id = ?) AS rounds,
                       (SELECT count(*) FROM review_settings WHERE brief_id = ? AND round = 2) AS round2,
                       (SELECT count(*) FROM audit_log WHERE entity_id = ?
                            AND action = 'submission.review_round_started') AS audit
                """, brief, brief, brief);
    }

    /**
     * IN_REVIEW brief with a completed fact-finding check, a finished round 1 (one submitted review, quorum 1)
     * and a revision submitted after round 1 was created; seeded through repositories, so no audit rows.
     */
    private UUID cameBackFromImprovement() {
        UUID id = transactions.execute(status -> {
            UUID brief = submissions.save(brief()).getId();
            FactFindingCheck check = new FactFindingCheck(brief, 1, "officer@example.test");
            check.complete(FactFindingResult.READY_FOR_REVIEW, Instant.parse("2026-11-10T09:00:00Z"));
            factFinding.save(check);
            settings.saveAndFlush(new ReviewSettings(brief, 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
            ReviewAssignment assignment = new ReviewAssignment(brief, 1, "committed-reviewer@example.test", null, 1,
                    COORDINATOR);
            assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
            assignment = assignments.saveAndFlush(assignment);
            reviews.save(new Review(assignment.getId(), ReviewDecision.CHANGES_NEEDED, "Needs work.", Instant.now()));
            RefinementRequest request = new RefinementRequest(brief, 1, RefinementSource.COORDINATOR, "Improve.",
                    LocalDate.of(2027, 1, 1), COORDINATOR, Instant.now());
            request.setStatus(RefinementStatus.RESPONDED);
            request.setRespondedAt(Instant.now().plusSeconds(5));
            RefinementRequest saved = requests.saveAndFlush(request);
            revisions.save(new CaseRevision(brief, 2, saved.getId(), "Changed.", OWNER, Instant.now().plusSeconds(5)));
            return brief;
        });
        briefs.add(id);
        return id;
    }

    private static Submission brief() {
        Submission brief = new Submission(UUID.randomUUID(), SubmissionCategory.GENERAL, "Committed round brief",
                "Purpose", "Problem", "receptionist@example.test", OWNER, null, null, null, null,
                Instant.parse("2026-11-01T08:00:00Z"));
        brief.updateSubmitterEmail(OWNER);
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-RC-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        for (SubmissionStage next : List.of(SubmissionStage.FORWARDED, SubmissionStage.IN_FACT_FINDING,
                SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW)) {
            brief.transitionTo(next, time);
        }
        return brief;
    }
}
