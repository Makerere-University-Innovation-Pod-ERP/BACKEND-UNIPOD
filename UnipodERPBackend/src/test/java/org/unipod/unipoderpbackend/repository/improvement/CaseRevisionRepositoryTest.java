package org.unipod.unipoderpbackend.repository.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
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
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;

// A failed statement aborts the PostgreSQL transaction, so each test fails at most once, last.
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class CaseRevisionRepositoryTest {
    private static final UUID BRIEF = new UUID(42L, 1);
    private static final String SUBMITTER = "submitter-42@example.test";
    private static final Instant SUBMITTED_AT = Instant.parse("2026-11-15T12:00:00.987654Z");

    @Autowired
    private CaseRevisionRepository repository;

    @Autowired
    private RefinementRequestRepository requests;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long firstRequest;
    private Long secondRequest;

    @BeforeEach
    void createRequests() {
        firstRequest = requests.saveAndFlush(request(BRIEF, 1)).getId();
        secondRequest = requests.saveAndFlush(request(BRIEF, 2)).getId();
    }

    @Test
    void savesAndReadsBackEveryField() {
        CaseRevision saved = repository.saveAndFlush(revision(2, firstRequest, "Added a costed budget."));
        entityManager.clear();

        CaseRevision reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getId(), reloaded.getId());
        assertEquals(BRIEF, reloaded.getBriefId());
        assertEquals(2, reloaded.getVersionNumber());
        assertEquals(firstRequest, reloaded.getRequestId());
        assertEquals("Added a costed budget.", reloaded.getWhatChanged());
        assertEquals(SUBMITTER, reloaded.getSubmittedBy());
        assertEquals(SUBMITTED_AT, reloaded.getSubmittedAt());
        assertEquals(0L, reloaded.getVersion());
    }

    @Test
    void versionIncrementsAndAStaleCopyFails() {
        CaseRevision saved = repository.saveAndFlush(revision(2, firstRequest, "v2"));
        entityManager.clear();

        CaseRevision stale = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(stale);

        // A revision has no mutable fields, so force the version forward the JPA way (written immediately).
        CaseRevision current = repository.findById(saved.getId()).orElseThrow();
        entityManager.getEntityManager().lock(current, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        entityManager.flush();
        assertEquals(1L, current.getVersion());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT version FROM case_revisions WHERE id = ?", Long.class, saved.getId()));
        entityManager.clear();

        assertEquals(0L, stale.getVersion());
        assertThrows(OptimisticLockingFailureException.class, () -> repository.saveAndFlush(stale));
    }

    @Test
    void duplicateBriefAndVersionIsRejected() {
        repository.saveAndFlush(revision(2, firstRequest, "v2"));

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(revision(2, secondRequest, "v2 again")));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("uq_case_revisions_brief_version"));
    }

    @Test
    void versionOneIsRejected() {
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(revision(1, firstRequest, "v1")));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("ck_case_revisions_version_number"));
    }

    @Test
    void versionsTwoAndThreeForTheSameBriefAreAccepted() {
        repository.saveAndFlush(revision(2, firstRequest, "v2"));
        repository.saveAndFlush(revision(3, secondRequest, "v3"));
        entityManager.clear();

        assertEquals(List.of(2, 3), repository.findByBriefIdOrderByVersionNumberAsc(BRIEF).stream()
                .map(CaseRevision::getVersionNumber).toList());
    }

    @Test
    void aSecondRevisionForTheSameRequestIsRejected() {
        repository.saveAndFlush(revision(2, firstRequest, "v2"));

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(revision(3, firstRequest, "v3 answering the same request")));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("uq_case_revisions_request"));
    }

    @Test
    void revisionForANonExistentRequestIsRejectedByTheForeignKey() {
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(revision(2, Long.MAX_VALUE, "Orphan.")));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("fk_case_revisions_request"));
    }

    @Test
    void nullWhatChangedIsRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(revision(2, firstRequest, null)));
    }

    @Test
    void whatChangedRoundTripsAtTwoThousandCharacters() {
        String text = "w".repeat(2000);
        CaseRevision saved = repository.saveAndFlush(revision(2, firstRequest, text));
        entityManager.clear();
        assertEquals(text, repository.findById(saved.getId()).orElseThrow().getWhatChanged());
    }

    @Test
    void whatChangedLongerThanTwoThousandIsRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(revision(2, firstRequest, "w".repeat(2001))));
    }

    @Test
    void repositoryFindsTheHighestVersionAndTheRevisionOfARequest() {
        assertTrue(repository.findTopByBriefIdOrderByVersionNumberDesc(BRIEF).isEmpty());
        CaseRevision v2 = repository.saveAndFlush(revision(2, firstRequest, "v2"));
        CaseRevision v3 = repository.saveAndFlush(revision(3, secondRequest, "v3"));
        Long unanswered = requests.saveAndFlush(request(BRIEF, 3)).getId();
        entityManager.clear();

        assertEquals(v3.getId(), repository.findTopByBriefIdOrderByVersionNumberDesc(BRIEF).orElseThrow().getId());
        assertEquals(v2.getId(), repository.findByRequestId(firstRequest).orElseThrow().getId());
        assertEquals(v3.getId(), repository.findByRequestId(secondRequest).orElseThrow().getId());
        assertTrue(repository.findByRequestId(unanswered).isEmpty());
        assertTrue(repository.findTopByBriefIdOrderByVersionNumberDesc(new UUID(42L, 99)).isEmpty());
    }

    @Test
    void revisionHasNoSetters() {
        assertTrue(Arrays.stream(CaseRevision.class.getMethods())
                .map(Method::getName).noneMatch(name -> name.startsWith("set")));
        assertTrue(Arrays.stream(CaseRevision.class.getDeclaredMethods())
                .map(Method::getName).noneMatch(name -> name.startsWith("set")));
    }

    private static CaseRevision revision(int versionNumber, Long requestId, String whatChanged) {
        return new CaseRevision(BRIEF, versionNumber, requestId, whatChanged, SUBMITTER, SUBMITTED_AT);
    }

    private static RefinementRequest request(UUID briefId, int cycle) {
        return new RefinementRequest(briefId, cycle, RefinementSource.COORDINATOR, "Please improve.",
                LocalDate.of(2026, 12, 1), "coordinator-42@example.test", Instant.parse("2026-11-12T09:00:00Z"));
    }
}
