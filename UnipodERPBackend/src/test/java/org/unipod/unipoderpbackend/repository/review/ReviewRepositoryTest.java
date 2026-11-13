package org.unipod.unipoderpbackend.repository.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class ReviewRepositoryTest {
    private static final String COORDINATOR = "coordinator-11@example.test";
    private static final Instant SUBMITTED_AT = Instant.parse("2026-11-11T10:15:30.123456Z");

    @Autowired
    private ReviewRepository repository;

    @Autowired
    private ReviewAssignmentRepository assignmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long assignmentId;
    private Long otherAssignmentId;

    @BeforeEach
    void createAssignments() {
        assignmentId = assignmentRepository.saveAndFlush(new ReviewAssignment(
                new UUID(0L, 901), 1, "reviewer-a@example.test", "Engineering", 1, COORDINATOR)).getId();
        otherAssignmentId = assignmentRepository.saveAndFlush(new ReviewAssignment(
                new UUID(0L, 901), 1, "reviewer-b@example.test", null, 2, COORDINATOR)).getId();
    }

    @Test
    void savesAndReadsBackEveryField() {
        Review saved = repository.saveAndFlush(
                new Review(assignmentId, ReviewDecision.CHANGES_NEEDED, "Clarify the budget.", SUBMITTED_AT));
        entityManager.refresh(saved);
        Long id = saved.getId();
        entityManager.clear();

        Review reloaded = repository.findById(id).orElseThrow();

        assertEquals(saved.getId(), reloaded.getId());
        assertEquals(saved.getAssignmentId(), reloaded.getAssignmentId());
        assertEquals(saved.getDecision(), reloaded.getDecision());
        assertEquals(saved.getComment(), reloaded.getComment());
        assertEquals(saved.getSubmittedAt(), reloaded.getSubmittedAt());
        assertEquals(saved.getVersion(), reloaded.getVersion());

        assertEquals(assignmentId, reloaded.getAssignmentId());
        assertEquals(ReviewDecision.CHANGES_NEEDED, reloaded.getDecision());
        assertEquals("Clarify the budget.", reloaded.getComment());
        assertEquals(SUBMITTED_AT, reloaded.getSubmittedAt());
        assertEquals(0L, reloaded.getVersion());
    }

    @Test
    void findsReviewByAssignmentId() {
        Review saved = repository.saveAndFlush(
                new Review(assignmentId, ReviewDecision.RECOMMENDED, "Strong proposal.", SUBMITTED_AT));
        entityManager.clear();

        assertEquals(saved.getId(), repository.findByAssignmentId(assignmentId).orElseThrow().getId());
        assertTrue(repository.findByAssignmentId(otherAssignmentId).isEmpty());
    }

    @Test
    void decisionIsStoredAsText() {
        Review saved = repository.saveAndFlush(
                new Review(assignmentId, ReviewDecision.NOT_RECOMMENDED, "Out of scope.", SUBMITTED_AT));

        String stored = jdbcTemplate.queryForObject(
                "SELECT decision FROM reviews WHERE id = ?", String.class, saved.getId());

        assertEquals("NOT_RECOMMENDED", stored);
    }

    @Test
    void rejectsSecondReviewForTheSameAssignment() {
        repository.saveAndFlush(new Review(assignmentId, ReviewDecision.RECOMMENDED, "First.", SUBMITTED_AT));

        Review second = new Review(assignmentId, ReviewDecision.NOT_RECOMMENDED, "Second.", SUBMITTED_AT);

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(second)
        );
        assertTrue(failure.getMostSpecificCause().getMessage().contains("uq_reviews_assignment"));
    }

    @Test
    void rejectsReviewWithNullComment() {
        Review invalid = new Review(assignmentId, ReviewDecision.RECOMMENDED, null, SUBMITTED_AT);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(invalid)
        );
    }

    @Test
    void databaseRejectsNullCommentInsertedDirectly() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO reviews (assignment_id, decision, comment, submitted_at) "
                                + "VALUES (?, 'RECOMMENDED', NULL, now())",
                        assignmentId)
        );
    }

    @Test
    void commentRoundTripsWithTwoThousandCharacters() {
        String comment = "c".repeat(2000);
        Review saved = repository.saveAndFlush(
                new Review(assignmentId, ReviewDecision.RECOMMENDED, comment, SUBMITTED_AT));
        entityManager.clear();

        Review reloaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals(2000, reloaded.getComment().length());
        assertEquals(comment, reloaded.getComment());
    }

    @Test
    void rejectsCommentLongerThanTwoThousandCharacters() {
        Review invalid = new Review(assignmentId, ReviewDecision.RECOMMENDED, "c".repeat(2001), SUBMITTED_AT);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(invalid)
        );
    }

    @Test
    void rejectsReviewForNonExistentAssignment() {
        Review orphan = new Review(Long.MAX_VALUE, ReviewDecision.RECOMMENDED, "No assignment.", SUBMITTED_AT);

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(orphan)
        );
        assertTrue(failure.getMostSpecificCause().getMessage().contains("fk_reviews_assignment"));
    }

    @Test
    void reviewHasNoSettersForImmutableFields() {
        Set<String> forbidden = Set.of("setDecision", "setComment", "setSubmittedAt");

        List<String> declared = Arrays.stream(Review.class.getDeclaredMethods()).map(Method::getName).toList();
        List<String> publicMethods = Arrays.stream(Review.class.getMethods()).map(Method::getName).toList();

        for (String name : forbidden) {
            assertTrue(declared.stream().noneMatch(name::equals), "Review must not declare " + name);
            assertTrue(publicMethods.stream().noneMatch(name::equals), "Review must not expose " + name);
        }
        assertTrue(
                declared.stream().noneMatch(name -> name.startsWith("set")),
                "Review must have no setters at all, found: " + declared
        );
    }
}
