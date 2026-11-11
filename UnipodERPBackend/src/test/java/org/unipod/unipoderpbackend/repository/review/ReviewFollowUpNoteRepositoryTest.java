package org.unipod.unipoderpbackend.repository.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewFollowUpNote;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class ReviewFollowUpNoteRepositoryTest {
    private static final String COORDINATOR = "coordinator-11@example.test";
    private static final String REVIEWER = "reviewer-a@example.test";

    @Autowired
    private ReviewFollowUpNoteRepository repository;

    @Autowired
    private ReviewAssignmentRepository assignmentRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long reviewId;
    private Long otherReviewId;

    @BeforeEach
    void createReviews() {
        reviewId = submittedReview(1, REVIEWER);
        otherReviewId = submittedReview(2, "reviewer-b@example.test");
    }

    @Test
    void savesAndReadsBackEveryField() {
        ReviewFollowUpNote saved = repository.saveAndFlush(
                new ReviewFollowUpNote(reviewId, "Budget clarified by phone.", REVIEWER));
        entityManager.refresh(saved);
        Long id = saved.getId();
        entityManager.clear();

        ReviewFollowUpNote reloaded = repository.findById(id).orElseThrow();

        assertEquals(saved.getId(), reloaded.getId());
        assertEquals(saved.getReviewId(), reloaded.getReviewId());
        assertEquals(saved.getNote(), reloaded.getNote());
        assertEquals(saved.getCreatedBy(), reloaded.getCreatedBy());
        assertEquals(saved.getCreatedAt(), reloaded.getCreatedAt());

        assertEquals(reviewId, reloaded.getReviewId());
        assertEquals("Budget clarified by phone.", reloaded.getNote());
        assertEquals(REVIEWER, reloaded.getCreatedBy());
    }

    @Test
    void createdAtIsSetAutomatically() {
        Instant before = Instant.now();
        ReviewFollowUpNote saved = repository.saveAndFlush(new ReviewFollowUpNote(reviewId, "Note.", REVIEWER));

        assertNotNull(saved.getCreatedAt());
        assertFalse(saved.getCreatedAt().isBefore(before));
        assertFalse(saved.getCreatedAt().isAfter(Instant.now()));
    }

    @Test
    void notesComeBackInCreatedOrder() {
        ReviewFollowUpNote first = repository.saveAndFlush(new ReviewFollowUpNote(reviewId, "First", REVIEWER));
        ReviewFollowUpNote second = repository.saveAndFlush(new ReviewFollowUpNote(reviewId, "Second", COORDINATOR));
        repository.saveAndFlush(new ReviewFollowUpNote(otherReviewId, "Other review", REVIEWER));
        ReviewFollowUpNote third = repository.saveAndFlush(new ReviewFollowUpNote(reviewId, "Third", REVIEWER));
        entityManager.clear();

        List<ReviewFollowUpNote> notes = repository.findByReviewIdOrderByCreatedAtAsc(reviewId);

        assertEquals(List.of("First", "Second", "Third"), notes.stream().map(ReviewFollowUpNote::getNote).toList());
        assertEquals(
                List.of(first.getId(), second.getId(), third.getId()),
                notes.stream().map(ReviewFollowUpNote::getId).toList()
        );
    }

    @Test
    void ordersByCreatedAtRatherThanInsertionOrder() {
        ReviewFollowUpNote a = repository.saveAndFlush(new ReviewFollowUpNote(reviewId, "Inserted first", REVIEWER));
        ReviewFollowUpNote b = repository.saveAndFlush(new ReviewFollowUpNote(reviewId, "Inserted second", REVIEWER));
        ReviewFollowUpNote c = repository.saveAndFlush(new ReviewFollowUpNote(reviewId, "Inserted third", REVIEWER));
        setCreatedAt(a.getId(), "2026-11-11T12:00:00Z");
        setCreatedAt(b.getId(), "2026-11-11T08:00:00Z");
        setCreatedAt(c.getId(), "2026-11-11T10:00:00Z");
        entityManager.clear();

        List<Long> ids = repository.findByReviewIdOrderByCreatedAtAsc(reviewId).stream()
                .map(ReviewFollowUpNote::getId)
                .toList();

        assertEquals(List.of(b.getId(), c.getId(), a.getId()), ids);
    }

    @Test
    void returnsNoNotesForReviewWithoutNotes() {
        assertTrue(repository.findByReviewIdOrderByCreatedAtAsc(otherReviewId).isEmpty());
    }

    @Test
    void rejectsNoteForNonExistentReview() {
        ReviewFollowUpNote orphan = new ReviewFollowUpNote(Long.MAX_VALUE, "No review.", REVIEWER);

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(orphan)
        );
        assertTrue(failure.getMostSpecificCause().getMessage().contains("fk_review_follow_up_notes_review"));
    }

    private void setCreatedAt(Long id, String instant) {
        jdbcTemplate.update(
                "UPDATE review_follow_up_notes SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse(instant)),
                id
        );
    }

    private Long submittedReview(int position, String reviewer) {
        Long assignmentId = assignmentRepository.saveAndFlush(new ReviewAssignment(
                new UUID(0L, 951), 1, reviewer, null, position, COORDINATOR)).getId();
        return reviewRepository.saveAndFlush(new Review(
                assignmentId, ReviewDecision.RECOMMENDED, "Looks good.", Instant.now())).getId();
    }
}
