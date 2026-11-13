package org.unipod.unipoderpbackend.controller.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * Runs without a surrounding test transaction so the service commits or rolls back for real:
 * a failure after the review row is written but before the assignment update leaves nothing behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewSubmitAtomicityTest {
    private static final String COORDINATOR = "coord@example.test";
    private static final String REVIEWER = "atomic-reviewer@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ReviewSettingsRepository settings;

    @MockitoSpyBean
    private ReviewAssignmentRepository assignments;

    @MockitoSpyBean
    private ReviewRepository reviews;

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

    @Test
    void assignmentUpdateFailureRollsBackTheSavedReview() throws Exception {
        UUID brief = briefWithAssignment();
        Mockito.doThrow(new IllegalStateException("assignment store unavailable"))
                .when(assignments)
                .saveAndFlush(Mockito.<ReviewAssignment>any());

        mockMvc.perform(post("/cases/{id}/reviews", brief)
                        .with(user(REVIEWER).roles("REVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RECOMMENDED\",\"comment\":\"Will roll back.\",\"assignmentVersion\":0}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.reference").isNotEmpty());

        // The review was written (and flushed) before the forced failure, then rolled back.
        InOrder order = Mockito.inOrder(reviews, assignments);
        order.verify(reviews).saveAndFlush(Mockito.<Review>any());
        order.verify(assignments).saveAndFlush(Mockito.<ReviewAssignment>any());

        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ?
                """, Integer.class, brief));
        Map<String, Object> assignment = jdbcTemplate.queryForMap(
                "SELECT status, version FROM review_assignments WHERE brief_id = ?", brief);
        assertEquals("NOT_STARTED", assignment.get("status"));
        assertEquals(0L, ((Number) assignment.get("version")).longValue());
        assertEquals("IN_REVIEW", jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?", String.class, brief));
    }

    private UUID briefWithAssignment() {
        UUID id = transactions.execute(status -> {
            Submission brief = new Submission(
                    UUID.randomUUID(),
                    SubmissionCategory.GENERAL,
                    "Atomic review brief",
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
            brief.register("CASE-RA-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            Instant time = Instant.parse("2026-11-01T10:00:00Z");
            brief.transitionTo(SubmissionStage.FORWARDED, time);
            brief.transitionTo(SubmissionStage.IN_FACT_FINDING, time);
            brief.transitionTo(SubmissionStage.IN_REVIEW, time);
            Submission saved = submissions.save(brief);
            settings.save(new ReviewSettings(saved.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
            assignments.save(new ReviewAssignment(saved.getId(), 1, REVIEWER, null, 1, COORDINATOR));
            return saved.getId();
        });
        briefs.add(id);
        return id;
    }
}
