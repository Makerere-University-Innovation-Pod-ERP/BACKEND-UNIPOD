package org.unipod.unipoderpbackend.repository.factfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class FactFindingRepositoryTest {
    private static final String OFFICER = "officer-77@example.test";
    private static final String OTHER_OFFICER = "officer-88@example.test";

    @Autowired
    private FactFindingRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createReferencedBriefs() {
        for (long value = 501; value <= 513; value++) {
            Submission brief = new Submission(
                    brief(value),
                    SubmissionCategory.GENERAL,
                    "Brief " + value,
                    "Purpose " + value,
                    "Problem " + value,
                    "receptionist@example.test",
                    "submitter@example.test",
                    null,
                    null,
                    null,
                    null,
                    Instant.parse("2026-11-01T08:00:00Z")
            );
            brief.assignTrackingReference("CASE-FF-" + value);
            entityManager.persist(brief);
        }
        entityManager.flush();
    }

    @Test
    void savesAndReadsBackEveryField() {
        FactFindingCheck check = populatedCheck(brief(501), 1);
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

        assertEquals(brief(501), reloaded.getBriefId());
        assertEquals(1, reloaded.getRound());
        assertEquals(OFFICER, reloaded.getOfficerId());
        assertEquals(Boolean.TRUE, reloaded.getTitleClear());
        assertEquals(Boolean.FALSE, reloaded.getRelevantToHub());
        assertEquals(FactFindingResult.READY_FOR_REVIEW, reloaded.getResult());
    }

    @Test
    void newCheckHasNoResultAndNoCompletionTime() {
        FactFindingCheck saved = repository.saveAndFlush(new FactFindingCheck(brief(502), 1, OFFICER));
        entityManager.clear();

        FactFindingCheck reloaded = repository.findById(saved.getId()).orElseThrow();

        assertNull(reloaded.getResult());
        assertNull(reloaded.getCompletedAt());
    }

    @Test
    void timestampsAreSetAutomatically() {
        FactFindingCheck saved = repository.saveAndFlush(new FactFindingCheck(brief(503), 1, OFFICER));

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
        repository.saveAndFlush(new FactFindingCheck(brief(504), 1, OFFICER));

        FactFindingCheck duplicate = new FactFindingCheck(brief(504), 1, OTHER_OFFICER);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicate)
        );
    }

    @Test
    void allowsDifferentRoundsForTheSameBrief() {
        FactFindingCheck roundOne = repository.saveAndFlush(new FactFindingCheck(brief(505), 1, OFFICER));
        FactFindingCheck roundTwo = repository.saveAndFlush(new FactFindingCheck(brief(505), 2, OTHER_OFFICER));

        assertNotNull(roundOne.getId());
        assertNotNull(roundTwo.getId());
        assertTrue(roundTwo.getId() > roundOne.getId());
    }

    @Test
    void latestRoundQueryReturnsHighestRound() {
        repository.saveAndFlush(new FactFindingCheck(brief(506), 1, OFFICER));
        FactFindingCheck roundTwo = repository.saveAndFlush(new FactFindingCheck(brief(506), 2, OTHER_OFFICER));

        FactFindingCheck latest = repository.findTopByBriefIdOrderByRoundDesc(brief(506)).orElseThrow();

        assertEquals(2, latest.getRound());
        assertEquals(roundTwo.getId(), latest.getId());
    }

    @Test
    void latestRoundQueryReturnsEmptyForUnknownBrief() {
        assertTrue(repository.findTopByBriefIdOrderByRoundDesc(brief(999_999)).isEmpty());
    }

    @Test
    void completedCheckIsDetectedByCompletionQuery() {
        FactFindingCheck check = repository.saveAndFlush(new FactFindingCheck(brief(508), 1, OFFICER));

        assertFalse(repository.existsByBriefIdAndRoundAndCompletedAtIsNotNull(brief(508), 1));

        check.setCompletedAt(Instant.now());
        repository.saveAndFlush(check);

        assertTrue(repository.existsByBriefIdAndRoundAndCompletedAtIsNotNull(brief(508), 1));
    }

    @Test
    void updateIncrementsVersionAndStaleCopyFails() {
        FactFindingCheck saved = repository.saveAndFlush(new FactFindingCheck(brief(509), 1, OFFICER));
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
        FactFindingCheck check = new FactFindingCheck(brief(510), 1, OFFICER);
        check.setResult(FactFindingResult.READY_FOR_REVIEW);
        FactFindingCheck saved = repository.saveAndFlush(check);

        String stored = jdbcTemplate.queryForObject(
                "SELECT result FROM fact_finding_checks WHERE id = ?",
                String.class,
                saved.getId()
        );

        assertEquals("READY_FOR_REVIEW", stored);
    }

    @Test
    void migrationRekeysBriefIdToUuidAndRecreatesConstraintAndIndex() {
        String briefType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'fact_finding_checks'
                  AND column_name = 'brief_id'
                """, String.class);
        assertEquals("uuid", briefType);

        String officerType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'fact_finding_checks'
                  AND column_name = 'officer_id'
                """, String.class);
        Integer officerLength = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'fact_finding_checks'
                  AND column_name = 'officer_id'
                """, Integer.class);
        assertEquals("character varying", officerType);
        assertEquals(254, officerLength);

        List<String> uniqueColumns = jdbcTemplate.queryForList("""
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_schema = tc.constraint_schema
                 AND kcu.constraint_name = tc.constraint_name
                 AND kcu.table_name = tc.table_name
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = 'fact_finding_checks'
                  AND tc.constraint_name = 'uq_fact_finding_checks_brief_round'
                  AND tc.constraint_type = 'UNIQUE'
                ORDER BY kcu.ordinal_position
                """, String.class);
        assertEquals(List.of("brief_id", "round"), uniqueColumns);

        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = 'fact_finding_checks'
                  AND indexname = 'ix_fact_finding_checks_brief_id'
                """, Integer.class);
        assertEquals(1, indexCount);
    }

    @Test
    void migrationWidensOfficerIdToVarchar254() {
        String type = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'fact_finding_checks'
                  AND column_name = 'officer_id'
                """, String.class);
        Integer length = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'fact_finding_checks'
                  AND column_name = 'officer_id'
                """, Integer.class);

        assertEquals("character varying", type);
        assertEquals(254, length);
    }

    @Test
    void notesRoundTripWithOneThousandCharacters() {
        String titleNote = repeated('t', 1000);
        String purposeNote = repeated('p', 1000);
        String evidenceNote = repeated('e', 1000);
        String relevantNote = repeated('r', 1000);

        FactFindingCheck check = new FactFindingCheck(brief(511), 1, OFFICER);
        check.setTitleNote(titleNote);
        check.setPurposeNote(purposeNote);
        check.setEvidenceNote(evidenceNote);
        check.setRelevantNote(relevantNote);

        FactFindingCheck saved = repository.saveAndFlush(check);
        entityManager.clear();
        FactFindingCheck reloaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals(1000, reloaded.getTitleNote().length());
        assertEquals(titleNote, reloaded.getTitleNote());
        assertEquals(1000, reloaded.getPurposeNote().length());
        assertEquals(purposeNote, reloaded.getPurposeNote());
        assertEquals(1000, reloaded.getEvidenceNote().length());
        assertEquals(evidenceNote, reloaded.getEvidenceNote());
        assertEquals(1000, reloaded.getRelevantNote().length());
        assertEquals(relevantNote, reloaded.getRelevantNote());
    }

    @Test
    void rejectsNoteLongerThanOneThousandCharacters() {
        FactFindingCheck check = new FactFindingCheck(brief(512), 1, OFFICER);
        check.setTitleNote(repeated('x', 1001));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(check)
        );
    }

    @Test
    void missingItemsRoundTripsWithTwoThousandCharacters() {
        String missingItems = repeated('m', 2000);

        FactFindingCheck check = new FactFindingCheck(brief(513), 1, OFFICER);
        check.setMissingItems(missingItems);

        FactFindingCheck saved = repository.saveAndFlush(check);
        entityManager.clear();
        FactFindingCheck reloaded = repository.findById(saved.getId()).orElseThrow();

        assertEquals(2000, reloaded.getMissingItems().length());
        assertEquals(missingItems, reloaded.getMissingItems());
    }

    private static String repeated(char value, int length) {
        return String.valueOf(value).repeat(length);
    }

    private static UUID brief(long value) {
        return new UUID(0L, value);
    }

    private static FactFindingCheck populatedCheck(UUID briefId, int round) {
        FactFindingCheck check = new FactFindingCheck(briefId, round, OFFICER);
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
