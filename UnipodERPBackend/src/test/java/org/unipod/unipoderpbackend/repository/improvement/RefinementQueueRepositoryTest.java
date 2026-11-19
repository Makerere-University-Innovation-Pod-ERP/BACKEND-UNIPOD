package org.unipod.unipoderpbackend.repository.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

/**
 * The staff refinement queue query against the real schema: which briefs appear, the latest request per brief,
 * the order and the paging. Briefs are created directly in the stage under test through the model's own
 * lifecycle transitions; requests are saved through the repository.
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class RefinementQueueRepositoryTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 10);
    private static final Instant BASE = Instant.parse("2026-12-01T08:00:00Z");
    private static final long NAMESPACE = 0x51A7E0L;

    private static final Map<SubmissionStage, List<SubmissionStage>> PATHS = Map.ofEntries(
            Map.entry(SubmissionStage.DRAFT, List.of()),
            Map.entry(SubmissionStage.SUBMITTED, List.of()),
            Map.entry(SubmissionStage.IN_FACT_FINDING, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING)),
            Map.entry(SubmissionStage.READY_FOR_REVIEW, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW)),
            Map.entry(SubmissionStage.IN_REVIEW, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW)),
            Map.entry(SubmissionStage.IMPROVEMENT_REQUESTED, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW,
                    SubmissionStage.IMPROVEMENT_REQUESTED)),
            Map.entry(SubmissionStage.RESUBMITTED, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW,
                    SubmissionStage.IMPROVEMENT_REQUESTED, SubmissionStage.RESUBMITTED)),
            Map.entry(SubmissionStage.COMPLETED, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW,
                    SubmissionStage.CONFIRMED_AS_PROJECT, SubmissionStage.IN_PROGRESS, SubmissionStage.COMPLETED)),
            Map.entry(SubmissionStage.CLOSED, List.of(SubmissionStage.FORWARDED,
                    SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW, SubmissionStage.IN_REVIEW,
                    SubmissionStage.CLOSED))
    );

    @Autowired
    private RefinementRequestRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- 1 --------------------------------------------------------------------------------

    @Test
    void noBriefInEitherStageGivesAnEmptyPage() {
        brief(1, SubmissionStage.IN_REVIEW);
        request(1, 1, RefinementStatus.OPEN, TODAY.plusDays(3));

        Page<RefinementQueueEntry> page = queue(25);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    // ---- 2-5: what is returned --------------------------------------------------------------

    @Test
    void improvementRequestedWithAnOpenRequestIsReturnedWithTheRequestValues() {
        Submission brief = brief(2, SubmissionStage.IMPROVEMENT_REQUESTED);
        RefinementRequest request = request(2, 1, RefinementStatus.OPEN, TODAY.plusDays(4));

        RefinementQueueEntry entry = single(queue(25));

        assertEquals(brief.getId(), entry.briefId());
        assertEquals("Queue brief 2", entry.title());
        assertEquals(brief.getCode(), entry.code());
        assertEquals(SubmissionCategory.GENERAL, entry.category());
        assertEquals(SubmissionStage.IMPROVEMENT_REQUESTED, entry.stage());
        assertEquals(storedUpdatedAt(2), entry.updatedAt());
        assertEquals(request.getId(), entry.requestId());
        assertEquals(1, entry.cycle());
        assertEquals(RefinementSource.COORDINATOR, entry.source());
        assertEquals(TODAY.plusDays(4), entry.dueDate());
        assertEquals(RefinementStatus.OPEN, entry.requestStatus());
    }

    @Test
    void improvementRequestedWithoutAnyRequestIsReturnedWithNullRequestFields() {
        Submission brief = brief(3, SubmissionStage.IMPROVEMENT_REQUESTED);

        RefinementQueueEntry entry = single(queue(25));

        assertEquals(brief.getId(), entry.briefId());
        assertEquals(SubmissionStage.IMPROVEMENT_REQUESTED, entry.stage());
        assertNull(entry.requestId());
        assertNull(entry.cycle());
        assertNull(entry.source());
        assertNull(entry.dueDate());
        assertNull(entry.requestStatus());
    }

    @Test
    void improvementRequestedWhoseLatestRequestIsRespondedIsReturned() {
        Submission brief = brief(4, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(4, 1, RefinementStatus.RESPONDED, TODAY.plusDays(2));

        RefinementQueueEntry entry = single(queue(25));

        assertEquals(brief.getId(), entry.briefId());
        assertEquals(1, entry.cycle());
        assertEquals(RefinementStatus.RESPONDED, entry.requestStatus());
    }

    @Test
    void resubmittedWhoseLatestRequestIsRespondedIsReturned() {
        Submission brief = brief(5, SubmissionStage.RESUBMITTED);
        request(5, 1, RefinementStatus.RESPONDED, TODAY.minusDays(1));

        RefinementQueueEntry entry = single(queue(25));

        assertEquals(brief.getId(), entry.briefId());
        assertEquals(SubmissionStage.RESUBMITTED, entry.stage());
        assertEquals(RefinementStatus.RESPONDED, entry.requestStatus());
        assertEquals(TODAY.minusDays(1), entry.dueDate());
    }

    // ---- 6-7: what is never returned ----------------------------------------------------------

    @Test
    void resubmittedWithAnOpenLatestRequestOrWithoutARequestIsNotReturned() {
        brief(6, SubmissionStage.RESUBMITTED);
        request(6, 1, RefinementStatus.RESPONDED, TODAY.plusDays(1));
        request(6, 2, RefinementStatus.OPEN, TODAY.plusDays(5));
        brief(7, SubmissionStage.RESUBMITTED);

        Page<RefinementQueueEntry> page = queue(25);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    @Test
    void briefsInOtherStagesAreNeverReturnedWhateverTheirRequests() {
        List<SubmissionStage> others = List.of(SubmissionStage.IN_REVIEW, SubmissionStage.READY_FOR_REVIEW,
                SubmissionStage.IN_FACT_FINDING, SubmissionStage.CLOSED, SubmissionStage.COMPLETED,
                SubmissionStage.DRAFT, SubmissionStage.SUBMITTED);
        long n = 100;
        for (SubmissionStage stage : others) {
            for (RefinementStatus status : RefinementStatus.values()) {
                brief(n, stage);
                request(n, 1, status, TODAY.minusDays(3));
                n++;
            }
            brief(n++, stage);
        }
        // An old RESPONDED request on a brief that was accepted and is back in review.
        brief(n, SubmissionStage.IN_REVIEW);
        request(n, 1, RefinementStatus.RESPONDED, TODAY.minusDays(20));
        request(n, 2, RefinementStatus.RESPONDED, TODAY.minusDays(10));

        Page<RefinementQueueEntry> page = queue(100);

        assertTrue(page.getContent().isEmpty());
        assertEquals(0, page.getTotalElements());
    }

    // ---- 8, 12: one row per brief, from the highest cycle ------------------------------------

    @Test
    void twoCyclesGiveOneRowWithTheHigherCyclesValues() {
        Submission brief = brief(8, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(8, 1, RefinementStatus.RESPONDED, TODAY.plusDays(1));
        RefinementRequest second = request(8, 2, RefinementStatus.OPEN, TODAY.plusDays(9));

        RefinementQueueEntry entry = single(queue(25));

        assertEquals(brief.getId(), entry.briefId());
        assertEquals(second.getId(), entry.requestId());
        assertEquals(2, entry.cycle());
        assertEquals(TODAY.plusDays(9), entry.dueDate());
        assertEquals(RefinementStatus.OPEN, entry.requestStatus());
    }

    @Test
    void threeCyclesOnOneBriefGiveThatBriefOnceAndATotalOfOne() {
        Submission brief = brief(12, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(12, 1, RefinementStatus.RESPONDED, TODAY.plusDays(1));
        request(12, 2, RefinementStatus.RESPONDED, TODAY.plusDays(2));
        request(12, 3, RefinementStatus.OPEN, TODAY.plusDays(3));

        Page<RefinementQueueEntry> page = queue(25);

        assertEquals(1, page.getTotalElements());
        assertEquals(List.of(brief.getId()), ids(page));
        assertEquals(3, page.getContent().getFirst().cycle());
    }

    // ---- 9: ordering --------------------------------------------------------------------------

    @Test
    void overdueFirstThenDueDateWithNullsLastThenNewestUpdateThenId() {
        // Overdue: open, due yesterday.
        UUID overdue = brief(901, SubmissionStage.IMPROVEMENT_REQUESTED).getId();
        request(901, 1, RefinementStatus.OPEN, TODAY.minusDays(1));
        // Earlier due date but not overdue: answered and waiting to be accepted.
        UUID earlierNotOverdue = brief(902, SubmissionStage.RESUBMITTED).getId();
        request(902, 1, RefinementStatus.RESPONDED, TODAY.minusDays(10));
        // Due today: not overdue.
        UUID dueToday = brief(903, SubmissionStage.IMPROVEMENT_REQUESTED).getId();
        request(903, 1, RefinementStatus.OPEN, TODAY);
        // Same due date and same update time: id ascending.
        UUID tieLowId = brief(904, SubmissionStage.IMPROVEMENT_REQUESTED).getId();
        request(904, 1, RefinementStatus.OPEN, TODAY.plusDays(3));
        UUID tieHighId = brief(905, SubmissionStage.IMPROVEMENT_REQUESTED).getId();
        request(905, 1, RefinementStatus.OPEN, TODAY.plusDays(3));
        // Same due date, different update times: newest first.
        UUID newer = brief(906, SubmissionStage.IMPROVEMENT_REQUESTED).getId();
        request(906, 1, RefinementStatus.OPEN, TODAY.plusDays(5));
        UUID older = brief(907, SubmissionStage.IMPROVEMENT_REQUESTED).getId();
        request(907, 1, RefinementStatus.OPEN, TODAY.plusDays(5));
        // No request (null due date): after every dated row, newest first.
        UUID legacyNewer = brief(908, SubmissionStage.IMPROVEMENT_REQUESTED).getId();
        UUID legacyOlder = brief(909, SubmissionStage.IMPROVEMENT_REQUESTED).getId();

        updatedAt(904, BASE);
        updatedAt(905, BASE);
        updatedAt(906, BASE.plusSeconds(60));
        updatedAt(907, BASE);
        updatedAt(908, BASE.plusSeconds(120));
        updatedAt(909, BASE);

        assertEquals(List.of(overdue, earlierNotOverdue, dueToday, tieLowId, tieHighId, newer, older,
                legacyNewer, legacyOlder), ids(queue(25)));
        assertTrue(tieLowId.compareTo(tieHighId) < 0);
    }

    // ---- 10-11: paging and the count query ------------------------------------------------

    @Test
    void twentyFiveRowsPageAsTenTenAndFiveInTheSameOrder() {
        for (int i = 0; i < 25; i++) {
            brief(1000 + i, SubmissionStage.IMPROVEMENT_REQUESTED);
            request(1000 + i, 1, RefinementStatus.OPEN, TODAY.plusDays(25 - i));
        }
        List<UUID> all = ids(queue(100));
        assertEquals(25, all.size());

        List<UUID> paged = new ArrayList<>();
        int[] expectedSizes = {10, 10, 5};
        for (int number = 0; number < 3; number++) {
            Page<RefinementQueueEntry> page = repository.findQueue(TODAY, PageRequest.of(number, 10));
            assertEquals(expectedSizes[number], page.getNumberOfElements());
            assertEquals(25, page.getTotalElements());
            assertEquals(3, page.getTotalPages());
            paged.addAll(ids(page));
        }

        assertEquals(all, paged);
        assertEquals(25, new HashSet<>(paged).size());
    }

    @Test
    void theCountMatchesTheRowsWhenLegacyBriefsAreMixedIn() {
        brief(1101, SubmissionStage.IMPROVEMENT_REQUESTED);
        brief(1102, SubmissionStage.IMPROVEMENT_REQUESTED);
        brief(1103, SubmissionStage.IMPROVEMENT_REQUESTED);
        brief(1104, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(1104, 1, RefinementStatus.RESPONDED, TODAY.plusDays(1));
        request(1104, 2, RefinementStatus.OPEN, TODAY.plusDays(2));
        brief(1105, SubmissionStage.RESUBMITTED);
        request(1105, 1, RefinementStatus.RESPONDED, TODAY.plusDays(3));

        Page<RefinementQueueEntry> page = queue(25);

        assertEquals(5, page.getTotalElements());
        assertEquals(5, page.getNumberOfElements());
        assertEquals(5, new HashSet<>(ids(page)).size());

        Page<RefinementQueueEntry> small = repository.findQueue(TODAY, PageRequest.of(0, 2));
        assertEquals(5, small.getTotalElements());
        assertEquals(3, small.getTotalPages());
    }

    // ---- counters (countQueue) ----------------------------------------------------------------

    @Test
    void anEmptyQueueCountsZerosNotNulls() {
        brief(2001, SubmissionStage.IN_REVIEW);
        request(2001, 1, RefinementStatus.OPEN, TODAY.minusDays(1));

        assertEquals(new RefinementQueueCounters(0, 0, 0, 0), counters());
    }

    @Test
    void oneBriefInEachStateIsCountedInItsState() {
        brief(2101, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(2101, 1, RefinementStatus.OPEN, TODAY.plusDays(2));
        brief(2102, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(2102, 1, RefinementStatus.OPEN, TODAY.minusDays(1));
        brief(2103, SubmissionStage.RESUBMITTED);
        request(2103, 1, RefinementStatus.RESPONDED, TODAY.minusDays(4));
        brief(2104, SubmissionStage.IMPROVEMENT_REQUESTED);
        brief(2105, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(2105, 1, RefinementStatus.RESPONDED, TODAY.plusDays(1));

        assertEquals(new RefinementQueueCounters(2, 1, 1, 2), counters());
    }

    @Test
    void dueTodayIsNotOverdueAndDueYesterdayIs() {
        brief(2201, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(2201, 1, RefinementStatus.OPEN, TODAY);
        assertEquals(new RefinementQueueCounters(1, 0, 0, 0), counters());

        brief(2202, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(2202, 1, RefinementStatus.OPEN, TODAY.minusDays(1));
        assertEquals(new RefinementQueueCounters(2, 1, 0, 0), counters());
    }

    @Test
    void aBriefWithTwoCyclesIsCountedOnceByItsLatestCycle() {
        // Cycle 1 open and overdue, cycle 2 answered: only cycle 2 counts (needs a request, not overdue).
        brief(2301, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(2301, 1, RefinementStatus.OPEN, TODAY.minusDays(5));
        request(2301, 2, RefinementStatus.RESPONDED, TODAY.plusDays(5));
        // Cycle 1 answered, cycle 2 open and overdue: waiting and overdue once.
        brief(2302, SubmissionStage.IMPROVEMENT_REQUESTED);
        request(2302, 1, RefinementStatus.RESPONDED, TODAY.plusDays(5));
        request(2302, 2, RefinementStatus.OPEN, TODAY.minusDays(2));

        assertEquals(new RefinementQueueCounters(1, 1, 0, 1), counters());
    }

    @Test
    void briefsInOtherStagesAreNotCounted() {
        long n = 2400;
        for (SubmissionStage stage : List.of(SubmissionStage.IN_REVIEW, SubmissionStage.READY_FOR_REVIEW,
                SubmissionStage.IN_FACT_FINDING, SubmissionStage.CLOSED, SubmissionStage.COMPLETED,
                SubmissionStage.DRAFT, SubmissionStage.SUBMITTED)) {
            brief(n, stage);
            request(n, 1, RefinementStatus.OPEN, TODAY.minusDays(3));
            n++;
            brief(n, stage);
            request(n, 1, RefinementStatus.RESPONDED, TODAY.plusDays(3));
            n++;
        }
        // Back in review after an accepted improvement, with old RESPONDED requests.
        brief(n, SubmissionStage.IN_REVIEW);
        request(n, 1, RefinementStatus.RESPONDED, TODAY.minusDays(20));
        request(n, 2, RefinementStatus.RESPONDED, TODAY.minusDays(10));

        assertEquals(new RefinementQueueCounters(0, 0, 0, 0), counters());
    }

    @Test
    void theCountersEqualTheRowsOfEachStateAcrossAllPages() {
        long n = 2500;
        for (int i = 0; i < 7; i++, n++) {
            brief(n, SubmissionStage.IMPROVEMENT_REQUESTED);
            request(n, 1, RefinementStatus.OPEN, TODAY.plusDays(i - 3));
        }
        for (int i = 0; i < 4; i++, n++) {
            brief(n, SubmissionStage.RESUBMITTED);
            request(n, 1, RefinementStatus.RESPONDED, TODAY.plusDays(i));
        }
        for (int i = 0; i < 3; i++, n++) {
            brief(n, SubmissionStage.IMPROVEMENT_REQUESTED);
        }
        for (int i = 0; i < 2; i++, n++) {
            brief(n, SubmissionStage.IMPROVEMENT_REQUESTED);
            request(n, 1, RefinementStatus.RESPONDED, TODAY.minusDays(i));
            request(n, 2, RefinementStatus.RESPONDED, TODAY.plusDays(i));
        }
        brief(n, SubmissionStage.RESUBMITTED);
        request(n, 1, RefinementStatus.OPEN, TODAY.minusDays(1));

        entityManager.flush();
        entityManager.clear();
        long waiting = 0;
        long overdue = 0;
        long ready = 0;
        long needs = 0;
        long rows = 0;
        for (int page = 0; ; page++) {
            Page<RefinementQueueEntry> current = repository.findQueue(TODAY, PageRequest.of(page, 4));
            for (RefinementQueueEntry entry : current.getContent()) {
                rows++;
                if (entry.stage() == SubmissionStage.RESUBMITTED) {
                    ready++;
                } else if (entry.requestStatus() == RefinementStatus.OPEN) {
                    waiting++;
                    if (entry.dueDate().isBefore(TODAY)) {
                        overdue++;
                    }
                } else {
                    needs++;
                }
            }
            if (!current.hasNext()) {
                break;
            }
        }

        assertEquals(16, rows);
        assertEquals(new RefinementQueueCounters(waiting, overdue, ready, needs), counters());
        assertEquals(new RefinementQueueCounters(7, 3, 4, 5), counters());
    }

    // ---- helpers -------------------------------------------------------------------------------

    private Page<RefinementQueueEntry> queue(int size) {
        entityManager.flush();
        entityManager.clear();
        return repository.findQueue(TODAY, PageRequest.of(0, size));
    }

    private RefinementQueueCounters counters() {
        entityManager.flush();
        entityManager.clear();
        return repository.countQueue(TODAY);
    }

    private static RefinementQueueEntry single(Page<RefinementQueueEntry> page) {
        assertEquals(1, page.getTotalElements());
        assertEquals(1, page.getNumberOfElements());
        return page.getContent().getFirst();
    }

    private static List<UUID> ids(Page<RefinementQueueEntry> page) {
        return page.getContent().stream().map(RefinementQueueEntry::briefId).toList();
    }

    private static UUID id(long n) {
        return new UUID(NAMESPACE, n);
    }

    /** A brief in {@code stage} through register and the model's lifecycle transitions; updatedAt is then fixed. */
    private Submission brief(long n, SubmissionStage stage) {
        Submission brief = new Submission(id(n), SubmissionCategory.GENERAL, "Queue brief " + n, "Purpose",
                "Problem", "receptionist@example.test", "submitter@example.test", null, null, null, null,
                Instant.parse("2026-11-01T08:00:00Z"));
        if (stage != SubmissionStage.DRAFT) {
            brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
            brief.register("CASE-RQ-" + n, Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            Instant time = Instant.parse("2026-11-01T10:00:00Z");
            for (SubmissionStage next : PATHS.get(stage)) {
                brief.transitionTo(next, time);
            }
        }
        entityManager.persist(brief);
        updatedAt(n, BASE.plusSeconds(n));
        return brief;
    }

    private RefinementRequest request(long brief, int cycle, RefinementStatus status, LocalDate dueDate) {
        RefinementRequest request = new RefinementRequest(id(brief), cycle, RefinementSource.COORDINATOR,
                "Improve cycle " + cycle, dueDate, "coordinator@example.test", BASE.plusSeconds(cycle));
        if (status == RefinementStatus.RESPONDED) {
            request.setStatus(RefinementStatus.RESPONDED);
            request.setRespondedAt(BASE.plusSeconds(cycle * 10L));
        }
        return repository.saveAndFlush(request);
    }

    /** Sets the stored updated_at so the order is deterministic (the entity sets it from the transition time). */
    private void updatedAt(long n, Instant value) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE submissions SET updated_at = ? WHERE id = ?", Timestamp.from(value), id(n));
    }

    private Instant storedUpdatedAt(long n) {
        return jdbcTemplate.queryForObject("SELECT updated_at FROM submissions WHERE id = ?", Timestamp.class, id(n))
                .toInstant();
    }
}
