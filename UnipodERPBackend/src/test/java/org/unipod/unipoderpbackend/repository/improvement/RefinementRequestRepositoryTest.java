package org.unipod.unipoderpbackend.repository.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class RefinementRequestRepositoryTest {
    private static final String COORDINATOR = "coordinator-40@example.test";
    private static final Instant REQUESTED_AT = Instant.parse("2026-11-12T09:30:00.123456Z");
    private static final LocalDate DUE = LocalDate.of(2026, 12, 1);

    @Autowired
    private RefinementRequestRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- 1: round trip --------------------------------------------------------------------

    @Test
    void savesAndReadsBackEveryField() {
        RefinementRequest saved = repository.saveAndFlush(request(brief(1), 1));
        entityManager.refresh(saved);
        Long id = saved.getId();
        entityManager.clear();

        RefinementRequest reloaded = repository.findById(id).orElseThrow();

        assertEquals(saved.getId(), reloaded.getId());
        assertEquals(brief(1), reloaded.getBriefId());
        assertEquals(1, reloaded.getCycle());
        assertEquals(RefinementSource.COORDINATOR, reloaded.getSource());
        assertEquals("Add a costed budget and two letters of support.", reloaded.getGuidanceNote());
        assertEquals(DUE, reloaded.getDueDate());
        assertEquals(RefinementStatus.OPEN, reloaded.getStatus());
        assertEquals(COORDINATOR, reloaded.getRequestedBy());
        assertEquals(REQUESTED_AT, reloaded.getRequestedAt());
        assertNull(reloaded.getRespondedAt());
        assertEquals(saved.getCreatedAt(), reloaded.getCreatedAt());
        assertEquals(saved.getUpdatedAt(), reloaded.getUpdatedAt());
        assertEquals(0L, reloaded.getVersion());
    }

    @Test
    void respondedAtRoundTripsAfterAResponse() {
        RefinementRequest saved = repository.saveAndFlush(request(brief(2), 1));
        Instant respondedAt = Instant.parse("2026-11-20T14:00:00.654321Z");
        saved.setStatus(RefinementStatus.RESPONDED);
        saved.setRespondedAt(respondedAt);
        repository.saveAndFlush(saved);
        entityManager.clear();

        RefinementRequest reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(RefinementStatus.RESPONDED, reloaded.getStatus());
        assertEquals(respondedAt, reloaded.getRespondedAt());
    }

    // ---- 2: timestamps and optimistic locking ---------------------------------------------

    @Test
    void timestampsAreSetAutomaticallyAndCompareAsStored() {
        RefinementRequest saved = repository.saveAndFlush(request(brief(3), 1));
        entityManager.clear();

        // Compare stored values with stored values only: PostgreSQL rounds TIMESTAMPTZ to microseconds.
        RefinementRequest created = repository.findById(saved.getId()).orElseThrow();
        assertTrue(created.getCreatedAt() != null && created.getUpdatedAt() != null);
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());

        created.setStatus(RefinementStatus.RESPONDED);
        repository.saveAndFlush(created);
        entityManager.clear();

        RefinementRequest updated = repository.findById(saved.getId()).orElseThrow();
        assertEquals(created.getCreatedAt(), updated.getCreatedAt(), "createdAt never changes");
        assertFalse(updated.getUpdatedAt().isBefore(updated.getCreatedAt()));
    }

    @Test
    void updateIncrementsVersionAndStaleCopyFails() {
        RefinementRequest saved = repository.saveAndFlush(request(brief(4), 1));
        assertEquals(0L, saved.getVersion());
        entityManager.clear();

        RefinementRequest stale = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(stale);

        RefinementRequest current = repository.findById(saved.getId()).orElseThrow();
        current.setStatus(RefinementStatus.RESPONDED);
        repository.saveAndFlush(current);
        assertEquals(1L, current.getVersion());

        stale.setRespondedAt(Instant.parse("2026-11-21T08:00:00Z"));
        assertThrows(OptimisticLockingFailureException.class, () -> repository.saveAndFlush(stale));
    }

    // ---- 3: enums as text and CHECK constraints -------------------------------------------

    @Test
    void enumsAreStoredAsText() {
        RefinementRequest fromFactFinding = new RefinementRequest(brief(5), 1, RefinementSource.FACT_FINDING,
                "Clarify the problem statement.", DUE, "officer-40@example.test", REQUESTED_AT);
        RefinementRequest saved = repository.saveAndFlush(fromFactFinding);
        saved.setStatus(RefinementStatus.RESPONDED);
        repository.saveAndFlush(saved);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT source, status FROM refinement_requests WHERE id = ?", saved.getId());
        assertEquals("FACT_FINDING", row.get("source"));
        assertEquals("RESPONDED", row.get("status"));
    }

    // A failed statement aborts the PostgreSQL transaction, so each test fails at most once, last.
    @Test
    void sourceCheckRejectsAnUnknownValue() {
        insert(brief(6), 1, "COORDINATOR", "OPEN");
        assertConstraint("ck_refinement_requests_source", () -> insert(brief(6), 2, "SUBMITTER", "OPEN"));
    }

    @Test
    void statusCheckRejectsAnUnknownValue() {
        insert(brief(6), 1, "FACT_FINDING", "RESPONDED");
        assertConstraint("ck_refinement_requests_status", () -> insert(brief(6), 2, "COORDINATOR", "CLOSED"));
    }

    // ---- 4: cycle rules, required fields, lengths, default status -------------------------

    @Test
    void duplicateBriefAndCycleIsRejected() {
        repository.saveAndFlush(request(brief(7), 1));

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(request(brief(7), 1)));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("uq_refinement_requests_brief_cycle"));
    }

    @Test
    void cycleZeroIsRejected() {
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(request(brief(8), 0)));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("ck_refinement_requests_cycle"));
    }

    @Test
    void nextCycleAndTheSameCycleOnAnotherBriefAreAccepted() {
        repository.saveAndFlush(request(brief(9), 1));
        repository.saveAndFlush(request(brief(9), 2));
        repository.saveAndFlush(request(brief(10), 1));

        assertEquals(2, repository.countByBriefId(brief(9)));
        assertEquals(1, repository.countByBriefId(brief(10)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"guidanceNote", "dueDate", "requestedBy"})
    void requiredFieldsAreRejectedWhenNullThroughJpa(String field) {
        RefinementRequest invalid = new RefinementRequest(brief(11), 1, RefinementSource.COORDINATOR,
                field.equals("guidanceNote") ? null : "Note",
                field.equals("dueDate") ? null : DUE,
                field.equals("requestedBy") ? null : COORDINATOR,
                REQUESTED_AT);
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(invalid));
    }

    @ParameterizedTest
    @ValueSource(strings = {"guidance_note", "due_date", "requested_by"})
    void databaseRejectsNullRequiredColumns(String column) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO refinement_requests (brief_id, cycle, source, guidance_note, due_date, requested_by,
                                                 requested_at, created_at, updated_at)
                VALUES (?, 1, 'COORDINATOR', %s, %s, %s, now(), now(), now())
                """.formatted(
                column.equals("guidance_note") ? "NULL" : "'Note'",
                column.equals("due_date") ? "NULL" : "'2026-12-01'",
                column.equals("requested_by") ? "NULL" : "'c@example.test'"), brief(12)));
    }

    @Test
    void guidanceNoteRoundTripsAtTwoThousandCharacters() {
        String note = "g".repeat(2000);
        RefinementRequest saved = repository.saveAndFlush(new RefinementRequest(
                brief(13), 1, RefinementSource.COORDINATOR, note, DUE, COORDINATOR, REQUESTED_AT));
        entityManager.clear();
        assertEquals(note, repository.findById(saved.getId()).orElseThrow().getGuidanceNote());
    }

    @Test
    void guidanceNoteLongerThanTwoThousandIsRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(new RefinementRequest(
                brief(14), 1, RefinementSource.COORDINATOR, "g".repeat(2001), DUE, COORDINATOR, REQUESTED_AT)));
    }

    @Test
    void defaultStatusIsOpenInTheEntityAndTheDatabase() {
        assertEquals(RefinementStatus.OPEN, request(brief(15), 1).getStatus());

        jdbcTemplate.update("""
                INSERT INTO refinement_requests (brief_id, cycle, source, guidance_note, due_date, requested_by,
                                                 requested_at, created_at, updated_at)
                VALUES (?, 1, 'COORDINATOR', 'Note', '2026-12-01', 'c@example.test', now(), now(), now())
                """, brief(15));
        assertEquals("OPEN", jdbcTemplate.queryForObject(
                "SELECT status FROM refinement_requests WHERE brief_id = ?", String.class, brief(15)));
    }

    // ---- 5: setters ---------------------------------------------------------------------

    @Test
    void onlyStatusAndRespondedAtHaveSetters() {
        List<String> setters = Arrays.stream(RefinementRequest.class.getDeclaredMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .sorted()
                .toList();
        assertEquals(List.of("setRespondedAt", "setStatus"), setters);
        assertEquals(List.of("setRespondedAt", "setStatus"), Arrays.stream(RefinementRequest.class.getMethods())
                .map(Method::getName).filter(name -> name.startsWith("set")).sorted().toList());
    }

    // ---- 8: repository methods ------------------------------------------------------------

    @Test
    void repositoryMethodsFindTheLatestCycleOpenStateAndCounts() {
        UUID brief = brief(16);
        assertTrue(repository.findTopByBriefIdOrderByCycleDesc(brief).isEmpty());
        assertFalse(repository.existsByBriefIdAndStatus(brief, RefinementStatus.OPEN));

        RefinementRequest first = repository.saveAndFlush(request(brief, 1));
        first.setStatus(RefinementStatus.RESPONDED);
        repository.saveAndFlush(first);
        RefinementRequest second = repository.saveAndFlush(request(brief, 2));
        repository.saveAndFlush(request(brief(17), 1));
        entityManager.clear();

        assertEquals(second.getId(), repository.findTopByBriefIdOrderByCycleDesc(brief).orElseThrow().getId());
        assertEquals(List.of(1, 2), repository.findByBriefIdOrderByCycleAsc(brief).stream()
                .map(RefinementRequest::getCycle).toList());
        assertTrue(repository.existsByBriefIdAndStatus(brief, RefinementStatus.OPEN));
        assertTrue(repository.existsByBriefIdAndStatus(brief, RefinementStatus.RESPONDED));
        assertEquals(2, repository.countByBriefId(brief));
        assertEquals(1, repository.countByBriefId(brief(17)));
        assertEquals(0, repository.countByBriefId(brief(18)));

        RefinementRequest latest = repository.findById(second.getId()).orElseThrow();
        latest.setStatus(RefinementStatus.RESPONDED);
        repository.saveAndFlush(latest);
        assertFalse(repository.existsByBriefIdAndStatus(brief, RefinementStatus.OPEN));
    }

    // ---- helpers --------------------------------------------------------------------------

    private static UUID brief(long n) {
        return new UUID(40L, n);
    }

    private static RefinementRequest request(UUID briefId, int cycle) {
        return new RefinementRequest(briefId, cycle, RefinementSource.COORDINATOR,
                "Add a costed budget and two letters of support.", DUE, COORDINATOR, REQUESTED_AT);
    }

    private void insert(UUID briefId, int cycle, String source, String status) {
        jdbcTemplate.update("""
                INSERT INTO refinement_requests (brief_id, cycle, source, guidance_note, due_date, status,
                                                 requested_by, requested_at, created_at, updated_at)
                VALUES (?, ?, ?, 'Note', '2026-12-01', ?, 'c@example.test', now(), now(), now())
                """, briefId, cycle, source, status);
    }

    private static void assertConstraint(String name, Runnable insert) {
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class, insert::run);
        assertTrue(failure.getMostSpecificCause().getMessage().contains(name), failure.getMessage());
    }
}
