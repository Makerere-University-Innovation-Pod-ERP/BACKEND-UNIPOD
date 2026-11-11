package org.unipod.unipoderpbackend.repository.factfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
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
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class FactFindingRepositoryTest {
    @Autowired
    private FactFindingRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void savesAndReadsBackEveryField() {
        FactFindingCheck check = populatedCheck(501L, 1);
        check.setResult(FactFindingResult.READY_FOR_REVIEW);
        check.setCompletedAt(Instant.parse("2026-11-11T09:30:00Z"));

        FactFindingCheck saved = repository.save(check);
        repository.flush();
        entityManager.refresh(saved);
        Long id = saved.getId();
        entityManager.clear();

        FactFindingCheck reloaded = repository.findById(id).orElseThrow();

        assertEquals(saved.getId(), reloaded.getId());
        assertEquals(saved.getBriefId(), reloaded.getBriefId());
        assertEquals(saved.getRound(), reloaded.getRound());
        assertEquals(saved.getOfficerId(), reloaded.getOfficerId());
        assertEquals(saved.getTitleClear(), reloaded.getTitleClear());
        assertEquals(saved.getTitleNote(), reloaded.getTitleNote());
        assertEquals(saved.getPurposeClear(), reloaded.getPurposeClear());
        assertEquals(saved.getPurposeNote(), reloaded.getPurposeNote());
        assertEquals(saved.getEvidenceEnough(), reloaded.getEvidenceEnough());
        assertEquals(saved.getEvidenceNote(), reloaded.getEvidenceNote());
        assertEquals(saved.getRelevantToHub(), reloaded.getRelevantToHub());
        assertEquals(saved.getRelevantNote(), reloaded.getRelevantNote());
        assertEquals(saved.getMissingItems(), reloaded.getMissingItems());
        assertEquals(saved.getResult(), reloaded.getResult());
        assertEquals(saved.getCompletedAt(), reloaded.getCompletedAt());
        assertEquals(saved.getCreatedAt(), reloaded.getCreatedAt());
        assertEquals(saved.getUpdatedAt(), reloaded.getUpdatedAt());
        assertEquals(saved.getVersion(), reloaded.getVersion());

        assertEquals(501L, reloaded.getBriefId());
        assertEquals(1, reloaded.getRound());
        assertEquals(77L, reloaded.getOfficerId());
        assertEquals(Boolean.TRUE, reloaded.getTitleClear());
        assertEquals(Boolean.FALSE, reloaded.getRelevantToHub());
        assertEquals(FactFindingResult.READY_FOR_REVIEW, reloaded.getResult());
    }

    @Test
    void newCheckHasNoResultAndNoCompletionTime() {
        FactFindingCheck saved = repository.saveAndFlush(new FactFindingCheck(502L, 1, 77L));
        entityManager.clear();

        FactFindingCheck reloaded = repository.findById(saved.getId()).orElseThrow();

        assertNull(reloaded.getResult());
        assertNull(reloaded.getCompletedAt());
    }

    @Test
    void timestampsAreSetAutomatically() {
        FactFindingCheck saved = repository.saveAndFlush(new FactFindingCheck(503L, 1, 77L));

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        assertFalse(saved.getCreatedAt().isAfter(Instant.now()));

        entityManager.clear();
        FactFindingCheck reloaded = repository.findById(saved.getId()).orElseThrow();
        reloaded.setTitleNote("Reviewer edit");
        FactFindingCheck updated = repository.saveAndFlush(reloaded);

        assertNotNull(updated.getUpdatedAt());
        assertFalse(updated.getUpdatedAt().isBefore(updated.getCreatedAt()));
    }

    @Test
    void rejectsDuplicateBriefAndRound() {
        repository.saveAndFlush(new FactFindingCheck(504L, 1, 77L));

        FactFindingCheck duplicate = new FactFindingCheck(504L, 1, 88L);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate)
        );
    }

    @Test
    void allowsDifferentRoundsForTheSameBrief() {
        FactFindingCheck roundOne = repository.saveAndFlush(new FactFindingCheck(505L, 1, 77L));
        FactFindingCheck roundTwo = repository.saveAndFlush(new FactFindingCheck(505L, 2, 78L));

        assertNotNull(roundOne.getId());
        assertNotNull(roundTwo.getId());
        assertTrue(roundTwo.getId() > roundOne.getId());
    }

    @Test
    void latestRoundQueryReturnsHighestRound() {
        repository.saveAndFlush(new FactFindingCheck(506L, 1, 77L));
        FactFindingCheck roundTwo = repository.saveAndFlush(new FactFindingCheck(506L, 2, 78L));

        FactFindingCheck latest = repository.findTopByBriefIdOrderByRoundDesc(506L).orElseThrow();

        assertEquals(2, latest.getRound());
        assertEquals(roundTwo.getId(), latest.getId());
    }

    @Test
    void latestRoundQueryReturnsEmptyForUnknownBrief() {
        assertTrue(repository.findTopByBriefIdOrderByRoundDesc(999_999L).isEmpty());
    }

    @Test
    void completedCheckIsDetectedByCompletionQuery() {
        FactFindingCheck check = repository.saveAndFlush(new FactFindingCheck(508L, 1, 77L));

        assertFalse(repository.existsByBriefIdAndRoundAndCompletedAtIsNotNull(508L, 1));

        check.setCompletedAt(Instant.now());
        repository.saveAndFlush(check);

        assertTrue(repository.existsByBriefIdAndRoundAndCompletedAtIsNotNull(508L, 1));
    }

    @Test
    void updateIncrementsVersionAndStaleCopyFails() {
        FactFindingCheck saved = repository.saveAndFlush(new FactFindingCheck(509L, 1, 77L));
        assertEquals(0L, saved.getVersion());
        entityManager.clear();

        FactFindingCheck stale = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(stale);

        FactFindingCheck current = repository.findById(saved.getId()).orElseThrow();
        current.setTitleNote("Newer edit");
        repository.saveAndFlush(current);
        assertEquals(1L, current.getVersion());

        stale.setTitleNote("Stale edit");

        assertThrows(
                OptimisticLockingFailureException.class,
                () -> repository.saveAndFlush(stale)
        );
    }

    @Test
    void enumIsStoredAsText() {
        FactFindingCheck check = new FactFindingCheck(510L, 1, 77L);
        check.setResult(FactFindingResult.READY_FOR_REVIEW);
        FactFindingCheck saved = repository.saveAndFlush(check);

        String stored = jdbcTemplate.queryForObject(
                "SELECT result FROM fact_finding_checks WHERE id = ?",
                String.class,
                saved.getId()
        );

        assertEquals("READY_FOR_REVIEW", stored);
    }

    private static FactFindingCheck populatedCheck(long briefId, int round) {
        FactFindingCheck check = new FactFindingCheck(briefId, round, 77L);
        check.setTitleClear(true);
        check.setTitleNote("Title is specific enough");
        check.setPurposeClear(true);
        check.setPurposeNote("Purpose is clear");
        check.setEvidenceEnough(false);
        check.setEvidenceNote("Needs a cost estimate");
        check.setRelevantToHub(false);
        check.setRelevantNote("Out of hub scope");
        check.setMissingItems("Cost estimate, prototype photos");
        return check;
    }
}
