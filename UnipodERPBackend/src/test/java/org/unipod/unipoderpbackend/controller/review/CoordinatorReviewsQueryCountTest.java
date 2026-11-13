package org.unipod.unipoderpbackend.controller.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
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

/** The coordinator's reviews view runs the same small number of SQL statements for 2 or 10 reviewers. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
class CoordinatorReviewsQueryCountTest {
    private static final String COORDINATOR = "coord@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

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

    @Test
    void statementCountDoesNotGrowWithReviewersOrNotes() throws Exception {
        UUID small = briefWith(2);
        UUID large = briefWith(10);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long forTwo = statementsFor(small, 2, statistics);
        long forTen = statementsFor(large, 10, statistics);

        assertEquals(forTwo, forTen, "statement count must not grow with the number of reviewers");
        assertTrue(forTen <= 7, "expected a small fixed number of statements, got " + forTen);
    }

    private long statementsFor(UUID brief, int expectedReviews, Statistics statistics) throws Exception {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        mockMvc.perform(get("/cases/{id}/reviews", brief).with(user(COORDINATOR).roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(expectedReviews))
                .andExpect(jsonPath("$.reviews[0].followUpNotes.length()").value(2))
                .andExpect(jsonPath("$.reviews[" + (expectedReviews - 1) + "].followUpNotes.length()").value(2));
        return statistics.getPrepareStatementCount();
    }

    private UUID briefWith(int reviewers) {
        Submission brief = new Submission(
                UUID.randomUUID(), SubmissionCategory.GENERAL, "Counted reviews", "Purpose", "Problem",
                "receptionist@example.test", "submitter@example.test", null, null, null, null,
                Instant.parse("2026-11-01T08:00:00Z"));
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-CQ-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        brief.transitionTo(SubmissionStage.FORWARDED, time);
        brief.transitionTo(SubmissionStage.IN_FACT_FINDING, time);
        brief.transitionTo(SubmissionStage.IN_REVIEW, time);
        UUID id = submissions.saveAndFlush(brief).getId();
        settings.saveAndFlush(new ReviewSettings(id, 1, ReviewMode.PARALLEL, reviewers, null, COORDINATOR));
        for (int i = 1; i <= reviewers; i++) {
            ReviewAssignment assignment = new ReviewAssignment(id, 1, "counted-" + i + "@example.test", null, i,
                    COORDINATOR);
            assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
            ReviewAssignment saved = assignments.saveAndFlush(assignment);
            Review review = reviews.saveAndFlush(new Review(saved.getId(), ReviewDecision.RECOMMENDED,
                    "Comment " + i, Instant.parse("2026-11-10T08:00:00Z").plusSeconds(i)));
            notes.saveAndFlush(new ReviewFollowUpNote(review.getId(), "Note " + i + "a", saved.getReviewerId()));
            notes.saveAndFlush(new ReviewFollowUpNote(review.getId(), "Note " + i + "b", saved.getReviewerId()));
        }
        return id;
    }
}
