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
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewFollowUpNote;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewFollowUpNoteRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * Real concurrent requests with committed data: the brief row lock serialises notes, so two
 * notes both succeed, and at 19 notes exactly one of two racing notes gets in (never 21, never 500).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewFollowUpNoteConcurrencyTest {
    private static final String COORDINATOR = "notes-race-coord@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private ReviewFollowUpNoteRepository notes;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    private final List<UUID> briefs = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            jdbcTemplate.update("""
                    DELETE FROM review_follow_up_notes WHERE review_id IN (
                        SELECT r.id FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                        WHERE a.brief_id = ?)
                    """, brief);
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
    void twoSimultaneousNotesBothSucceed() throws Exception {
        String reviewer = "notes-race-" + UUID.randomUUID() + "@example.test";
        Seeded seeded = briefWithSubmittedReview(reviewer, 0);

        assertEquals(List.of(201, 201), concurrently(noteCall(seeded.brief(), reviewer), noteCall(seeded.brief(), reviewer)));
        assertEquals(2, noteCount(seeded.reviewId()));
        assertEquals(2, auditRows(seeded.brief()));
    }

    @RepeatedTest(3)
    void atNineteenNotesExactlyOneOfTwoRacingNotesGetsIn() throws Exception {
        String reviewer = "notes-race-" + UUID.randomUUID() + "@example.test";
        Seeded seeded = briefWithSubmittedReview(reviewer, 19);

        assertEquals(List.of(201, 409), concurrently(noteCall(seeded.brief(), reviewer), noteCall(seeded.brief(), reviewer)));
        assertEquals(20, noteCount(seeded.reviewId()));
        assertEquals(1, auditRows(seeded.brief()));
    }

    private Callable<Integer> noteCall(UUID brief, String reviewer) {
        return () -> mockMvc.perform(post("/cases/{id}/my-review/follow-up-notes", brief)
                        .with(user(reviewer).roles("REVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Race.\"}"))
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

    private int noteCount(long reviewId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM review_follow_up_notes WHERE review_id = ?", Integer.class, reviewId);
    }

    private int auditRows(UUID brief) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'submission.review_follow_up_added'",
                Integer.class, brief);
    }

    private record Seeded(UUID brief, long reviewId) {
    }

    private Seeded briefWithSubmittedReview(String reviewer, int existingNotes) {
        Seeded seeded = transactions.execute(status -> {
            Submission brief = new Submission(
                    UUID.randomUUID(), SubmissionCategory.GENERAL, "Racing notes brief", "Purpose", "Problem",
                    "receptionist@example.test", "submitter@example.test", null, null, null, null,
                    Instant.parse("2026-11-01T08:00:00Z"));
            brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
            brief.register("CASE-NR-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            Instant time = Instant.parse("2026-11-01T10:00:00Z");
            brief.transitionTo(SubmissionStage.FORWARDED, time);
            brief.transitionTo(SubmissionStage.IN_FACT_FINDING, time);
            brief.transitionTo(SubmissionStage.IN_REVIEW, time);
            Submission saved = submissions.save(brief);
            settings.save(new ReviewSettings(saved.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
            ReviewAssignment assignment = new ReviewAssignment(saved.getId(), 1, reviewer, null, 1, COORDINATOR);
            assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
            ReviewAssignment stored = assignments.saveAndFlush(assignment);
            Review review = reviews.saveAndFlush(
                    new Review(stored.getId(), ReviewDecision.RECOMMENDED, "Done.", Instant.now()));
            for (int i = 0; i < existingNotes; i++) {
                notes.save(new ReviewFollowUpNote(review.getId(), "Existing " + i, reviewer));
            }
            return new Seeded(saved.getId(), review.getId());
        });
        briefs.add(seeded.brief());
        return seeded;
    }
}
