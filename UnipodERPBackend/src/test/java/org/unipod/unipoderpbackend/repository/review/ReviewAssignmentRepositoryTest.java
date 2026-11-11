package org.unipod.unipoderpbackend.repository.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class ReviewAssignmentRepositoryTest {
    private static final String COORDINATOR = "coordinator-11@example.test";
    private static final String REVIEWER_A = "reviewer-a@example.test";
    private static final String REVIEWER_B = "reviewer-b@example.test";
    private static final String REVIEWER_C = "reviewer-c@example.test";

    @Autowired
    private ReviewAssignmentRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndReadsBackEveryField() {
        ReviewAssignment saved = repository.saveAndFlush(
                new ReviewAssignment(brief(801), 1, REVIEWER_A, "Engineering", 1, COORDINATOR));
        entityManager.refresh(saved);
        Long id = saved.getId();
        entityManager.clear();

        ReviewAssignment reloaded = repository.findById(id).orElseThrow();

        assertEquals(saved.getId(), reloaded.getId());
        assertEquals(saved.getBriefId(), reloaded.getBriefId());
        assertEquals(saved.getRound(), reloaded.getRound());
        assertEquals(saved.getReviewerId(), reloaded.getReviewerId());
        assertEquals(saved.getDepartment(), reloaded.getDepartment());
        assertEquals(saved.getPosition(), reloaded.getPosition());
        assertEquals(saved.getStatus(), reloaded.getStatus());
        assertEquals(saved.getAssignedBy(), reloaded.getAssignedBy());
        assertEquals(saved.getCreatedAt(), reloaded.getCreatedAt());
        assertEquals(saved.getUpdatedAt(), reloaded.getUpdatedAt());
        assertEquals(saved.getVersion(), reloaded.getVersion());

        assertEquals(brief(801), reloaded.getBriefId());
        assertEquals(1, reloaded.getRound());
        assertEquals(REVIEWER_A, reloaded.getReviewerId());
        assertEquals("Engineering", reloaded.getDepartment());
        assertEquals(1, reloaded.getPosition());
        assertEquals(ReviewAssignmentStatus.NOT_STARTED, reloaded.getStatus());
        assertEquals(COORDINATOR, reloaded.getAssignedBy());
        assertEquals(0L, reloaded.getVersion());
    }

    @Test
    void timestampsAreSetAutomatically() {
        ReviewAssignment saved = repository.saveAndFlush(
                new ReviewAssignment(brief(802), 1, REVIEWER_A, null, 1, COORDINATOR));

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        assertFalse(saved.getCreatedAt().isAfter(Instant.now()));

        entityManager.clear();
        ReviewAssignment reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.setStatus(ReviewAssignmentStatus.SUBMITTED);
        ReviewAssignment updated = repository.saveAndFlush(reloaded);

        assertNotNull(updated.getUpdatedAt());
        assertFalse(updated.getUpdatedAt().isBefore(updated.getCreatedAt()));
    }

    @Test
    void updateIncrementsVersionAndStaleCopyFails() {
        ReviewAssignment saved = repository.saveAndFlush(
                new ReviewAssignment(brief(803), 1, REVIEWER_A, null, 1, COORDINATOR));
        assertEquals(0L, saved.getVersion());
        entityManager.clear();

        ReviewAssignment stale = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(stale);

        ReviewAssignment current = repository.findById(saved.getId()).orElseThrow();
        current.setStatus(ReviewAssignmentStatus.SUBMITTED);
        repository.saveAndFlush(current);
        assertEquals(1L, current.getVersion());

        stale.setDepartment("Stale edit");

        assertThrows(
                OptimisticLockingFailureException.class,
                () -> repository.saveAndFlush(stale)
        );
    }

    @Test
    void statusIsStoredAsText() {
        ReviewAssignment assignment = new ReviewAssignment(brief(804), 1, REVIEWER_A, null, 1, COORDINATOR);
        assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
        ReviewAssignment saved = repository.saveAndFlush(assignment);

        String stored = jdbcTemplate.queryForObject(
                "SELECT status FROM review_assignments WHERE id = ?", String.class, saved.getId());

        assertEquals("SUBMITTED", stored);
    }

    @Test
    void rejectsDuplicateReviewerInTheSameBriefAndRound() {
        repository.saveAndFlush(new ReviewAssignment(brief(805), 1, REVIEWER_A, null, 1, COORDINATOR));

        ReviewAssignment duplicate = new ReviewAssignment(brief(805), 1, REVIEWER_A, null, 2, COORDINATOR);

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate)
        );
        assertTrue(failure.getMostSpecificCause().getMessage()
                .contains("uq_review_assignments_brief_round_reviewer"));
    }

    @Test
    void rejectsDuplicatePositionInTheSameBriefAndRound() {
        repository.saveAndFlush(new ReviewAssignment(brief(806), 1, REVIEWER_A, null, 1, COORDINATOR));

        ReviewAssignment duplicate = new ReviewAssignment(brief(806), 1, REVIEWER_B, null, 1, COORDINATOR);

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate)
        );
        assertTrue(failure.getMostSpecificCause().getMessage()
                .contains("uq_review_assignments_brief_round_position"));
    }

    @Test
    void acceptsTheSameReviewerInRoundTwo() {
        ReviewAssignment roundOne = repository.saveAndFlush(
                new ReviewAssignment(brief(807), 1, REVIEWER_A, null, 1, COORDINATOR));
        ReviewAssignment roundTwo = repository.saveAndFlush(
                new ReviewAssignment(brief(807), 2, REVIEWER_A, null, 1, COORDINATOR));

        assertNotNull(roundOne.getId());
        assertNotNull(roundTwo.getId());
        assertTrue(roundTwo.getId() > roundOne.getId());
    }

    @Test
    void returnsAssignmentsInPositionOrderEvenWhenInsertedOutOfOrder() {
        repository.saveAndFlush(new ReviewAssignment(brief(808), 1, REVIEWER_C, null, 3, COORDINATOR));
        repository.saveAndFlush(new ReviewAssignment(brief(808), 1, REVIEWER_A, null, 1, COORDINATOR));
        repository.saveAndFlush(new ReviewAssignment(brief(808), 1, REVIEWER_B, null, 2, COORDINATOR));
        repository.saveAndFlush(new ReviewAssignment(brief(808), 2, REVIEWER_A, null, 1, COORDINATOR));
        repository.saveAndFlush(new ReviewAssignment(brief(809), 1, REVIEWER_A, null, 1, COORDINATOR));
        entityManager.clear();

        List<ReviewAssignment> ordered = repository.findByBriefIdAndRoundOrderByPositionAsc(brief(808), 1);

        assertEquals(List.of(1, 2, 3), ordered.stream().map(ReviewAssignment::getPosition).toList());
        assertEquals(
                List.of(REVIEWER_A, REVIEWER_B, REVIEWER_C),
                ordered.stream().map(ReviewAssignment::getReviewerId).toList()
        );
    }

    @Test
    void countsAssignmentsByStatusWithinBriefAndRound() {
        ReviewAssignment first = new ReviewAssignment(brief(810), 1, REVIEWER_A, null, 1, COORDINATOR);
        first.setStatus(ReviewAssignmentStatus.SUBMITTED);
        repository.saveAndFlush(first);
        ReviewAssignment second = new ReviewAssignment(brief(810), 1, REVIEWER_B, null, 2, COORDINATOR);
        second.setStatus(ReviewAssignmentStatus.SUBMITTED);
        repository.saveAndFlush(second);
        repository.saveAndFlush(new ReviewAssignment(brief(810), 1, REVIEWER_C, null, 3, COORDINATOR));
        ReviewAssignment otherRound = new ReviewAssignment(brief(810), 2, REVIEWER_A, null, 1, COORDINATOR);
        otherRound.setStatus(ReviewAssignmentStatus.SUBMITTED);
        repository.saveAndFlush(otherRound);
        ReviewAssignment otherBrief = new ReviewAssignment(brief(811), 1, REVIEWER_A, null, 1, COORDINATOR);
        otherBrief.setStatus(ReviewAssignmentStatus.SUBMITTED);
        repository.saveAndFlush(otherBrief);

        assertEquals(2L, repository.countByBriefIdAndRoundAndStatus(brief(810), 1, ReviewAssignmentStatus.SUBMITTED));
        assertEquals(1L, repository.countByBriefIdAndRoundAndStatus(brief(810), 1, ReviewAssignmentStatus.NOT_STARTED));
        assertEquals(1L, repository.countByBriefIdAndRoundAndStatus(brief(810), 2, ReviewAssignmentStatus.SUBMITTED));
        assertEquals(0L, repository.countByBriefIdAndRoundAndStatus(brief(810), 2, ReviewAssignmentStatus.NOT_STARTED));
        assertEquals(0L, repository.countByBriefIdAndRoundAndStatus(brief(999_999), 1, ReviewAssignmentStatus.SUBMITTED));

        ReviewAssignment third = repository.findByBriefIdAndRoundOrderByPositionAsc(brief(810), 1).get(2);
        third.setStatus(ReviewAssignmentStatus.SUBMITTED);
        repository.saveAndFlush(third);

        assertEquals(3L, repository.countByBriefIdAndRoundAndStatus(brief(810), 1, ReviewAssignmentStatus.SUBMITTED));
        assertEquals(0L, repository.countByBriefIdAndRoundAndStatus(brief(810), 1, ReviewAssignmentStatus.NOT_STARTED));
    }

    @Test
    void findsOnlyTheGivenReviewersRowsWithTheGivenStatus() {
        ReviewAssignment mineOpenOne = repository.saveAndFlush(
                new ReviewAssignment(brief(812), 1, REVIEWER_A, null, 1, COORDINATOR));
        ReviewAssignment mineOpenTwo = repository.saveAndFlush(
                new ReviewAssignment(brief(813), 1, REVIEWER_A, null, 1, COORDINATOR));
        ReviewAssignment mineDone = new ReviewAssignment(brief(814), 1, REVIEWER_A, null, 1, COORDINATOR);
        mineDone.setStatus(ReviewAssignmentStatus.SUBMITTED);
        repository.saveAndFlush(mineDone);
        repository.saveAndFlush(new ReviewAssignment(brief(812), 1, REVIEWER_B, null, 2, COORDINATOR));
        repository.saveAndFlush(new ReviewAssignment(brief(815), 1, REVIEWER_C, null, 1, COORDINATOR));

        List<ReviewAssignment> open = repository.findByReviewerIdAndStatus(REVIEWER_A, ReviewAssignmentStatus.NOT_STARTED);

        assertEquals(2, open.size());
        assertTrue(open.stream().allMatch(a -> REVIEWER_A.equals(a.getReviewerId())));
        assertTrue(open.stream().allMatch(a -> a.getStatus() == ReviewAssignmentStatus.NOT_STARTED));
        assertEquals(
                List.of(mineOpenOne.getId(), mineOpenTwo.getId()),
                open.stream().map(ReviewAssignment::getId).sorted().toList()
        );

        List<ReviewAssignment> done = repository.findByReviewerIdAndStatus(REVIEWER_A, ReviewAssignmentStatus.SUBMITTED);
        assertEquals(List.of(mineDone.getId()), done.stream().map(ReviewAssignment::getId).toList());

        assertTrue(repository.findByReviewerIdAndStatus("nobody@example.test", ReviewAssignmentStatus.NOT_STARTED)
                .isEmpty());
    }

    private static UUID brief(long value) {
        return new UUID(0L, value);
    }
}
