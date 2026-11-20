package org.unipod.unipoderpbackend.repository.factfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.ServiceRequester;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/**
 * The fact-finding queue query (screen F1) against the real schema. Briefs are created directly in the stage and
 * category under test through register, forward and the model's lifecycle transitions; checks are persisted directly.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class FactFindingQueueRepositoryTest {
    private static final long NAMESPACE = 0xF1F1L;
    private static final Instant BASE = Instant.parse("2026-11-01T08:00:00Z");
    private static final String OFFICER = "f1-officer@example.test";

    private static final Map<SubmissionStage, List<SubmissionStage>> AFTER_FORWARD = Map.of(
            SubmissionStage.FORWARDED, List.of(),
            SubmissionStage.IN_FACT_FINDING, List.of(SubmissionStage.IN_FACT_FINDING),
            SubmissionStage.READY_FOR_REVIEW, List.of(SubmissionStage.IN_FACT_FINDING,
                    SubmissionStage.READY_FOR_REVIEW),
            SubmissionStage.IN_REVIEW, List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW),
            SubmissionStage.IMPROVEMENT_REQUESTED, List.of(SubmissionStage.IN_FACT_FINDING,
                    SubmissionStage.IMPROVEMENT_REQUESTED),
            SubmissionStage.RESUBMITTED, List.of(SubmissionStage.IN_FACT_FINDING,
                    SubmissionStage.IMPROVEMENT_REQUESTED, SubmissionStage.RESUBMITTED),
            SubmissionStage.CLOSED, List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW,
                    SubmissionStage.CLOSED)
    );

    @Autowired
    private FactFindingQueueRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Sector sector;

    // ---- 1-4 --------------------------------------------------------------------------------

    @Test
    void noBriefInEitherStageGivesAnEmptyPage() {
        brief(1, SubmissionStage.READY_FOR_REVIEW, SubmissionCategory.GENERAL);
        brief(2, SubmissionStage.SUBMITTED, SubmissionCategory.STUDENT);

        Page<FactFindingQueueEntry> page = queue(25);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void aForwardedBriefWithoutACheckIsReturnedWithNullCheckFields() {
        Submission brief = brief(10, SubmissionStage.FORWARDED, SubmissionCategory.GENERAL);

        FactFindingQueueEntry entry = single(queue(25));

        assertEquals(brief.getId(), entry.briefId());
        assertEquals("F1 brief 10", entry.title());
        assertEquals(brief.getCode(), entry.code());
        assertEquals(SubmissionCategory.GENERAL, entry.category());
        assertEquals(SubmissionStage.FORWARDED, entry.stage());
        assertEquals(storedInstant(10, "forwarded_at"), entry.forwardedAt());
        assertEquals(storedInstant(10, "updated_at"), entry.updatedAt());
        assertNull(entry.checkId());
        assertNull(entry.checkOfficerId());
        assertNull(entry.checkUpdatedAt());
    }

    @Test
    void anInFactFindingBriefWithASavedDraftCarriesTheCheck() {
        brief(20, SubmissionStage.IN_FACT_FINDING, SubmissionCategory.STUDENT);
        FactFindingCheck draft = draft(20);

        FactFindingQueueEntry entry = single(queue(25));

        assertEquals(SubmissionStage.IN_FACT_FINDING, entry.stage());
        assertEquals(SubmissionCategory.STUDENT, entry.category());
        assertEquals(draft.getId(), entry.checkId());
        assertEquals(OFFICER, entry.checkOfficerId());
        assertEquals(storedCheckUpdatedAt(draft.getId()), entry.checkUpdatedAt());
    }

    @Test
    void aForwardedBriefWithADraftCheckIsReturnedWithTheCheck() {
        brief(30, SubmissionStage.FORWARDED, SubmissionCategory.GENERAL);
        FactFindingCheck draft = draft(30);

        FactFindingQueueEntry entry = single(queue(25));

        assertEquals(SubmissionStage.FORWARDED, entry.stage());
        assertEquals(draft.getId(), entry.checkId());
        assertEquals(OFFICER, entry.checkOfficerId());
    }

    // ---- inferred from the F1 definition ----------------------------------------------------------

    @Test
    void serviceRequestsAreNeverReturnedInEitherStage() {
        brief(40, SubmissionStage.FORWARDED, SubmissionCategory.SERVICE_REQUEST);
        brief(41, SubmissionStage.IN_FACT_FINDING, SubmissionCategory.SERVICE_REQUEST);
        draft(41);
        Submission general = brief(42, SubmissionStage.FORWARDED, SubmissionCategory.GENERAL);

        Page<FactFindingQueueEntry> page = queue(25);

        assertEquals(List.of(general.getId()), ids(page));
        assertEquals(1, page.getTotalElements());
    }

    @ParameterizedTest
    @EnumSource(value = SubmissionStage.class, names = {"SUBMITTED", "READY_FOR_REVIEW", "IN_REVIEW",
            "IMPROVEMENT_REQUESTED", "RESUBMITTED", "CLOSED"})
    void briefsInOtherStagesAreNeverReturned(SubmissionStage stage) {
        brief(50, stage, SubmissionCategory.GENERAL);
        brief(51, stage, SubmissionCategory.STUDENT);
        if (stage != SubmissionStage.SUBMITTED) {
            completedCheck(50, FactFindingResult.READY_FOR_REVIEW);
        }

        Page<FactFindingQueueEntry> page = queue(25);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void aCompletedCheckOnABriefStillInTheseStagesIsReportedAsItIs() {
        brief(60, SubmissionStage.IN_FACT_FINDING, SubmissionCategory.GENERAL);
        FactFindingCheck done = completedCheck(60, FactFindingResult.NEEDS_MORE_INFORMATION);

        FactFindingQueueEntry entry = single(queue(25));

        assertEquals(done.getId(), entry.checkId());
    }

    @Test
    void oldestForwardedFirstThenNullsLastThenId() {
        UUID newest = brief(71, SubmissionStage.FORWARDED, SubmissionCategory.GENERAL).getId();
        UUID oldest = brief(72, SubmissionStage.IN_FACT_FINDING, SubmissionCategory.GENERAL).getId();
        UUID tieLow = brief(73, SubmissionStage.FORWARDED, SubmissionCategory.STUDENT).getId();
        UUID tieHigh = brief(74, SubmissionStage.FORWARDED, SubmissionCategory.GENERAL).getId();
        UUID neverForwarded = brief(75, SubmissionStage.FORWARDED, SubmissionCategory.GENERAL).getId();
        forwardedAt(71, BASE.plusSeconds(300));
        forwardedAt(72, BASE.plusSeconds(100));
        forwardedAt(73, BASE.plusSeconds(200));
        forwardedAt(74, BASE.plusSeconds(200));
        forwardedAt(75, null);

        assertEquals(List.of(oldest, tieLow, tieHigh, newest, neverForwarded), ids(queue(25)));
        assertTrue(tieLow.compareTo(tieHigh) < 0);
    }

    @Test
    void twentyFiveRowsPageAsTenTenAndFiveInTheSameOrder() {
        for (int i = 0; i < 25; i++) {
            brief(100 + i, i % 2 == 0 ? SubmissionStage.FORWARDED : SubmissionStage.IN_FACT_FINDING,
                    SubmissionCategory.GENERAL);
            forwardedAt(100 + i, BASE.plusSeconds(25 - i));
        }
        List<UUID> all = ids(queue(100));
        assertEquals(25, all.size());

        List<UUID> paged = new ArrayList<>();
        int[] sizes = {10, 10, 5};
        for (int number = 0; number < 3; number++) {
            Page<FactFindingQueueEntry> page = repository.findQueue(PageRequest.of(number, 10));
            assertEquals(sizes[number], page.getNumberOfElements());
            assertEquals(25, page.getTotalElements());
            assertEquals(3, page.getTotalPages());
            paged.addAll(ids(page));
        }
        assertEquals(all, paged);
        assertEquals(25, new HashSet<>(paged).size());
    }

    @Test
    void theCountMatchesTheRowsWithAndWithoutChecks() {
        brief(201, SubmissionStage.FORWARDED, SubmissionCategory.GENERAL);
        brief(202, SubmissionStage.IN_FACT_FINDING, SubmissionCategory.GENERAL);
        draft(202);
        brief(203, SubmissionStage.IN_FACT_FINDING, SubmissionCategory.STUDENT);
        brief(204, SubmissionStage.FORWARDED, SubmissionCategory.SERVICE_REQUEST);

        Page<FactFindingQueueEntry> page = queue(25);
        assertEquals(3, page.getTotalElements());
        assertEquals(3, page.getNumberOfElements());

        Page<FactFindingQueueEntry> small = repository.findQueue(PageRequest.of(0, 2));
        assertEquals(3, small.getTotalElements());
        assertEquals(2, small.getTotalPages());
    }

    // ---- added: types, every excluded stage, one row per brief ------------------------------------

    @Test
    void studentAndGeneralBriefsAreReturnedInBothStagesAndServiceRequestsAreNot() {
        Set<UUID> expected = new HashSet<>();
        long n = 300;
        for (SubmissionStage stage : List.of(SubmissionStage.FORWARDED, SubmissionStage.IN_FACT_FINDING)) {
            for (SubmissionCategory category : SubmissionCategory.values()) {
                Submission brief = brief(n++, stage, category);
                if (category != SubmissionCategory.SERVICE_REQUEST) {
                    expected.add(brief.getId());
                }
            }
        }

        Page<FactFindingQueueEntry> page = queue(25);

        assertEquals(expected, new HashSet<>(ids(page)));
        assertEquals(4, page.getTotalElements());
        assertTrue(page.getContent().stream().noneMatch(entry -> entry.category() == SubmissionCategory.SERVICE_REQUEST));
    }

    @ParameterizedTest
    @EnumSource(value = SubmissionStage.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"FORWARDED", "IN_FACT_FINDING"})
    void everyOtherStageIsExcludedWithAndWithoutACompletedCheck(SubmissionStage stage) {
        // Every stage outside (FORWARDED, IN_FACT_FINDING) can be seeded: DRAFT is never registered,
        // AWAITING_INFORMATION comes from requestInformation, the rest from forward and the model's transitions.
        // fact_finding_checks has no stage constraint, so a completed check can exist for every stage.
        anyStage(400, stage, SubmissionCategory.GENERAL);
        anyStage(401, stage, SubmissionCategory.STUDENT);
        completedCheck(401, FactFindingResult.READY_FOR_REVIEW);

        assertEquals(stage, storedStage(400));
        assertEquals(stage, storedStage(401));
        Page<FactFindingQueueEntry> page = queue(25);
        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void aStrayRoundTwoCheckDoesNotDuplicateTheBriefAndRoundOneIsShown() {
        Submission brief = brief(500, SubmissionStage.IN_FACT_FINDING, SubmissionCategory.GENERAL);
        FactFindingCheck roundOne = draft(500);
        jdbcTemplate.update("""
                INSERT INTO fact_finding_checks (brief_id, round, officer_id, created_at, updated_at, version)
                VALUES (?, 2, 'stray-round-two@example.test', ?, ?, 0)
                """, brief.getId(), Timestamp.from(BASE.plusSeconds(9000)), Timestamp.from(BASE.plusSeconds(9000)));
        brief(501, SubmissionStage.FORWARDED, SubmissionCategory.GENERAL);

        Page<FactFindingQueueEntry> page = queue(25);

        assertEquals(2, page.getTotalElements());
        assertEquals(2, page.getNumberOfElements());
        List<FactFindingQueueEntry> rows = page.getContent().stream()
                .filter(entry -> entry.briefId().equals(brief.getId())).toList();
        assertEquals(1, rows.size());
        assertEquals(roundOne.getId(), rows.getFirst().checkId());
        assertEquals(OFFICER, rows.getFirst().checkOfficerId());
        Page<FactFindingQueueEntry> small = repository.findQueue(PageRequest.of(0, 1));
        assertEquals(2, small.getTotalElements());
        assertEquals(2, small.getTotalPages());
    }

    // ---- helpers ------------------------------------------------------------------------------

    private Page<FactFindingQueueEntry> queue(int size) {
        entityManager.flush();
        entityManager.clear();
        return repository.findQueue(PageRequest.of(0, size));
    }

    private static FactFindingQueueEntry single(Page<FactFindingQueueEntry> page) {
        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getNumberOfElements());
        return page.getContent().getFirst();
    }

    private static List<UUID> ids(Page<FactFindingQueueEntry> page) {
        return page.getContent().stream().map(FactFindingQueueEntry::briefId).toList();
    }

    private static UUID id(long n) {
        return new UUID(NAMESPACE, n);
    }

    /** A brief in {@code stage}: register, then forward to a sector, then the model's lifecycle transitions. */
    private Submission brief(long n, SubmissionStage stage, SubmissionCategory category) {
        boolean student = category == SubmissionCategory.STUDENT;
        Submission brief = new Submission(id(n), category, "F1 brief " + n, "Purpose", "Problem",
                "receptionist@example.test", "submitter@example.test",
                student ? "2100700" + n : null, student ? "BSc Computer Science" : null,
                student ? "Dr. Supervisor" : null, student ? RecommendationLetterStatus.SUBMITTED : null, BASE);
        if (category == SubmissionCategory.SERVICE_REQUEST) {
            // The model's rule for service requests: the service, the expected result and a preferred time.
            brief.applyIntakeDetails(null, "3D printing", ServiceRequester.STUDENT, null, "A printed prototype",
                    BASE.plusSeconds(86_400), "2026-01");
        } else {
            brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        }
        brief.register("CASE-F1-" + n, BASE.plusSeconds(60), "receptionist@example.test");
        if (stage != SubmissionStage.SUBMITTED) {
            brief.forward(sector(), BASE.plusSeconds(n));
            for (SubmissionStage next : AFTER_FORWARD.get(stage)) {
                brief.transitionTo(next, BASE.plusSeconds(n));
            }
        }
        entityManager.persist(brief);
        return brief;
    }

    /** Any stage, including DRAFT, SUBMITTED, AWAITING_INFORMATION and the post-review stages. */
    private Submission anyStage(long n, SubmissionStage stage, SubmissionCategory category) {
        if (stage == SubmissionStage.DRAFT) {
            Submission draft = new Submission(id(n), category, "F1 brief " + n, "Purpose", "Problem",
                    "receptionist@example.test", "submitter@example.test", null, null, null, null, BASE);
            entityManager.persist(draft);
            return draft;
        }
        if (stage == SubmissionStage.AWAITING_INFORMATION) {
            Submission brief = brief(n, SubmissionStage.SUBMITTED, category);
            brief.requestInformation("Please add the supervisor contact.", "receptionist@example.test",
                    BASE.plusSeconds(120));
            entityManager.persist(brief);
            return brief;
        }
        List<SubmissionStage> path = switch (stage) {
            case CONFIRMED_AS_PROJECT -> List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW,
                    SubmissionStage.CONFIRMED_AS_PROJECT);
            case AWAITING_PAYMENT -> List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW,
                    SubmissionStage.AWAITING_PAYMENT);
            case IN_PROGRESS -> List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW,
                    SubmissionStage.CONFIRMED_AS_PROJECT, SubmissionStage.IN_PROGRESS);
            case COMPLETED -> List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW,
                    SubmissionStage.CONFIRMED_AS_PROJECT, SubmissionStage.IN_PROGRESS, SubmissionStage.COMPLETED);
            default -> null;
        };
        if (path == null) {
            return brief(n, stage, category);
        }
        Submission brief = brief(n, SubmissionStage.FORWARDED, category);
        for (SubmissionStage next : path) {
            brief.transitionTo(next, BASE.plusSeconds(n));
        }
        entityManager.persist(brief);
        return brief;
    }

    private SubmissionStage storedStage(long n) {
        entityManager.flush();
        return SubmissionStage.valueOf(jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?", String.class, id(n)));
    }

    private Sector sector() {
        if (sector == null) {
            sector = entityManager.persist(new Sector(UUID.randomUUID(), "F1 sector " + UUID.randomUUID(),
                    "f1-sector@example.test", true, BASE, BASE));
        }
        return sector;
    }

    private FactFindingCheck draft(long brief) {
        FactFindingCheck check = new FactFindingCheck(id(brief), 1, OFFICER);
        check.setMissingItems("Budget");
        return entityManager.persistAndFlush(check);
    }

    private FactFindingCheck completedCheck(long brief, FactFindingResult result) {
        FactFindingCheck check = new FactFindingCheck(id(brief), 1, OFFICER);
        check.complete(result, BASE.plusSeconds(3600));
        return entityManager.persistAndFlush(check);
    }

    private void forwardedAt(long n, Instant value) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE submissions SET forwarded_at = ? WHERE id = ?",
                value == null ? null : Timestamp.from(value), id(n));
    }

    private Instant storedInstant(long n, String column) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM submissions WHERE id = ?", Timestamp.class,
                id(n)).toInstant();
    }

    private Instant storedCheckUpdatedAt(Long checkId) {
        return jdbcTemplate.queryForObject("SELECT updated_at FROM fact_finding_checks WHERE id = ?",
                Timestamp.class, checkId).toInstant();
    }
}
