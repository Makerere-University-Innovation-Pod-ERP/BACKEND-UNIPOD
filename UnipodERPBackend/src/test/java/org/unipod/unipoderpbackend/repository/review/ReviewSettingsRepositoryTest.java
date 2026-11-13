package org.unipod.unipoderpbackend.repository.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
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
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class ReviewSettingsRepositoryTest {
    private static final String COORDINATOR = "coordinator-11@example.test";
    private static final String OTHER_COORDINATOR = "coordinator-22@example.test";

    @Autowired
    private ReviewSettingsRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndReadsBackEveryField() {
        ReviewSettings settings = new ReviewSettings(
                brief(701), 1, ReviewMode.SEQUENTIAL, 3, LocalDate.parse("2026-12-15"), COORDINATOR);

        ReviewSettings saved = repository.saveAndFlush(settings);
        entityManager.refresh(saved);
        Long id = saved.getId();
        entityManager.clear();

        ReviewSettings reloaded = repository.findById(id).orElseThrow();

        assertEquals(saved.getId(), reloaded.getId());
        assertEquals(saved.getBriefId(), reloaded.getBriefId());
        assertEquals(saved.getRound(), reloaded.getRound());
        assertEquals(saved.getMode(), reloaded.getMode());
        assertEquals(saved.getReviewsNeeded(), reloaded.getReviewsNeeded());
        assertEquals(saved.getDueDate(), reloaded.getDueDate());
        assertEquals(saved.getSetBy(), reloaded.getSetBy());
        assertEquals(saved.getCreatedAt(), reloaded.getCreatedAt());
        assertEquals(saved.getUpdatedAt(), reloaded.getUpdatedAt());
        assertEquals(saved.getVersion(), reloaded.getVersion());

        assertEquals(brief(701), reloaded.getBriefId());
        assertEquals(1, reloaded.getRound());
        assertEquals(ReviewMode.SEQUENTIAL, reloaded.getMode());
        assertEquals(3, reloaded.getReviewsNeeded());
        assertEquals(LocalDate.parse("2026-12-15"), reloaded.getDueDate());
        assertEquals(COORDINATOR, reloaded.getSetBy());
        assertEquals(0L, reloaded.getVersion());
    }

    @Test
    void dueDateIsOptional() {
        ReviewSettings saved = repository.saveAndFlush(
                new ReviewSettings(brief(702), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        entityManager.clear();

        assertNull(repository.findById(saved.getId()).orElseThrow().getDueDate());
    }

    @Test
    void timestampsAreSetAutomatically() {
        ReviewSettings saved = repository.saveAndFlush(
                new ReviewSettings(brief(703), 1, ReviewMode.PARALLEL, 2, null, COORDINATOR));

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        assertFalse(saved.getCreatedAt().isAfter(Instant.now()));
        Instant createdAt = saved.getCreatedAt();

        entityManager.clear();
        ReviewSettings reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.setReviewsNeeded(3);
        ReviewSettings updated = repository.saveAndFlush(reloaded);

        // Never compare a stored timestamp with an in-memory one: PostgreSQL rounds TIMESTAMPTZ to
        // microseconds, and on Windows Instant.now() keeps 100 ns digits and often returns the same
        // value for the insert and the update, so a reloaded createdAt can round up past updatedAt.
        // Compare in-memory with in-memory, and stored with stored.
        assertNotNull(updated.getUpdatedAt());
        assertFalse(updated.getUpdatedAt().isBefore(createdAt));

        entityManager.clear();
        ReviewSettings stored = repository.findById(saved.getId()).orElseThrow();
        assertNotNull(stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());
        assertFalse(stored.getUpdatedAt().isBefore(stored.getCreatedAt()));
    }

    @Test
    void updateIncrementsVersionAndStaleCopyFails() {
        ReviewSettings saved = repository.saveAndFlush(
                new ReviewSettings(brief(704), 1, ReviewMode.PARALLEL, 2, null, COORDINATOR));
        assertEquals(0L, saved.getVersion());
        entityManager.clear();

        ReviewSettings stale = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(stale);

        ReviewSettings current = repository.findById(saved.getId()).orElseThrow();
        current.setMode(ReviewMode.SEQUENTIAL);
        repository.saveAndFlush(current);
        assertEquals(1L, current.getVersion());

        stale.setReviewsNeeded(5);

        assertThrows(
                OptimisticLockingFailureException.class,
                () -> repository.saveAndFlush(stale)
        );
    }

    @Test
    void modeIsStoredAsText() {
        ReviewSettings saved = repository.saveAndFlush(
                new ReviewSettings(brief(705), 1, ReviewMode.SEQUENTIAL, 1, null, COORDINATOR));

        String stored = jdbcTemplate.queryForObject(
                "SELECT mode FROM review_settings WHERE id = ?", String.class, saved.getId());

        assertEquals("SEQUENTIAL", stored);
    }

    @Test
    void rejectsDuplicateBriefAndRound() {
        repository.saveAndFlush(new ReviewSettings(brief(706), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));

        ReviewSettings duplicate = new ReviewSettings(
                brief(706), 1, ReviewMode.SEQUENTIAL, 2, null, OTHER_COORDINATOR);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate)
        );
    }

    @Test
    void rejectsZeroReviewsNeeded() {
        ReviewSettings invalid = new ReviewSettings(brief(707), 1, ReviewMode.PARALLEL, 0, null, COORDINATOR);

        DataIntegrityViolationException failure = assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(invalid)
        );
        assertTrue(failure.getMostSpecificCause().getMessage().contains("ck_review_settings_reviews_needed"));
    }

    @Test
    void acceptsRoundTwoForTheSameBrief() {
        ReviewSettings roundOne = repository.saveAndFlush(
                new ReviewSettings(brief(708), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        ReviewSettings roundTwo = repository.saveAndFlush(
                new ReviewSettings(brief(708), 2, ReviewMode.SEQUENTIAL, 2, null, OTHER_COORDINATOR));

        assertNotNull(roundOne.getId());
        assertNotNull(roundTwo.getId());
        assertTrue(roundTwo.getId() > roundOne.getId());
    }

    @Test
    void findsSettingsByBriefAndRound() {
        repository.saveAndFlush(new ReviewSettings(brief(709), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        ReviewSettings roundTwo = repository.saveAndFlush(
                new ReviewSettings(brief(709), 2, ReviewMode.SEQUENTIAL, 2, null, COORDINATOR));

        ReviewSettings found = repository.findByBriefIdAndRound(brief(709), 2).orElseThrow();

        assertEquals(roundTwo.getId(), found.getId());
        assertTrue(repository.findByBriefIdAndRound(brief(709), 3).isEmpty());
    }

    @Test
    void latestRoundQueryReturnsHighestRound() {
        repository.saveAndFlush(new ReviewSettings(brief(710), 2, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        ReviewSettings roundThree = repository.saveAndFlush(
                new ReviewSettings(brief(710), 3, ReviewMode.PARALLEL, 1, null, COORDINATOR));
        repository.saveAndFlush(new ReviewSettings(brief(710), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));

        ReviewSettings latest = repository.findTopByBriefIdOrderByRoundDesc(brief(710)).orElseThrow();

        assertEquals(3, latest.getRound());
        assertEquals(roundThree.getId(), latest.getId());
        assertTrue(repository.findTopByBriefIdOrderByRoundDesc(brief(999_999)).isEmpty());
    }

    private static UUID brief(long value) {
        return new UUID(0L, value);
    }
}
