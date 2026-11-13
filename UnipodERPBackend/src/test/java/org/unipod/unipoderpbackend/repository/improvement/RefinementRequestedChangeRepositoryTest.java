package org.unipod.unipoderpbackend.repository.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequestedChange;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;

// A failed statement aborts the PostgreSQL transaction, so each test fails at most once, last.
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class RefinementRequestedChangeRepositoryTest {
    @Autowired
    private RefinementRequestedChangeRepository repository;

    @Autowired
    private RefinementRequestRepository requests;

    @Autowired
    private TestEntityManager entityManager;

    private Long requestId;
    private Long otherRequestId;

    @BeforeEach
    void createRequests() {
        requestId = requests.saveAndFlush(request(new UUID(41L, 1), 1)).getId();
        otherRequestId = requests.saveAndFlush(request(new UUID(41L, 2), 1)).getId();
    }

    @Test
    void savesAndReadsBackEveryField() {
        RefinementRequestedChange saved = repository.saveAndFlush(
                new RefinementRequestedChange(requestId, 1, "Add a costed budget."));
        entityManager.clear();

        RefinementRequestedChange reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getId(), reloaded.getId());
        assertEquals(requestId, reloaded.getRequestId());
        assertEquals(1, reloaded.getPosition());
        assertEquals("Add a costed budget.", reloaded.getText());
    }

    @Test
    void duplicateRequestAndPositionIsRejected() {
        repository.saveAndFlush(new RefinementRequestedChange(requestId, 1, "First."));

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new RefinementRequestedChange(requestId, 1, "Second.")));
        assertTrue(failure.getMostSpecificCause().getMessage()
                .contains("uq_refinement_requested_changes_request_position"));
    }

    @Test
    void positionZeroIsRejected() {
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new RefinementRequestedChange(requestId, 0, "Zero.")));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("ck_refinement_requested_changes_position"));
    }

    @Test
    void textRoundTripsAtFiveHundredCharacters() {
        String text = "t".repeat(500);
        RefinementRequestedChange saved = repository.saveAndFlush(new RefinementRequestedChange(requestId, 1, text));
        entityManager.clear();
        assertEquals(text, repository.findById(saved.getId()).orElseThrow().getText());
    }

    @Test
    void textLongerThanFiveHundredIsRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new RefinementRequestedChange(requestId, 1, "t".repeat(501))));
    }

    @Test
    void nullTextIsRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new RefinementRequestedChange(requestId, 1, null)));
    }

    @Test
    void changeForANonExistentRequestIsRejectedByTheForeignKey() {
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new RefinementRequestedChange(Long.MAX_VALUE, 1, "Orphan.")));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("fk_refinement_requested_changes_request"));
    }

    @Test
    void itemsComeBackInPositionOrderEvenWhenInsertedOutOfOrder() {
        for (int position : List.of(3, 1, 4, 2)) {
            repository.saveAndFlush(new RefinementRequestedChange(requestId, position, "Item " + position));
        }
        entityManager.clear();

        assertEquals(List.of(1, 2, 3, 4), repository.findByRequestIdOrderByPositionAsc(requestId).stream()
                .map(RefinementRequestedChange::getPosition).toList());
        assertEquals(List.of("Item 1", "Item 2", "Item 3", "Item 4"),
                repository.findByRequestIdOrderByPositionAsc(requestId).stream()
                        .map(RefinementRequestedChange::getText).toList());
        assertTrue(repository.findByRequestIdOrderByPositionAsc(otherRequestId).isEmpty());
    }

    @Test
    void itemsOfSeveralRequestsAreGroupedByRequestThenPosition() {
        Long first = Math.min(requestId, otherRequestId);
        Long second = Math.max(requestId, otherRequestId);
        repository.saveAndFlush(new RefinementRequestedChange(second, 2, "B2"));
        repository.saveAndFlush(new RefinementRequestedChange(first, 2, "A2"));
        repository.saveAndFlush(new RefinementRequestedChange(second, 1, "B1"));
        repository.saveAndFlush(new RefinementRequestedChange(first, 1, "A1"));
        Long unrelated = requests.saveAndFlush(request(new UUID(41L, 3), 1)).getId();
        repository.saveAndFlush(new RefinementRequestedChange(unrelated, 1, "C1"));
        entityManager.clear();

        assertEquals(List.of("A1", "A2", "B1", "B2"),
                repository.findByRequestIdInOrderByRequestIdAscPositionAsc(List.of(second, first)).stream()
                        .map(RefinementRequestedChange::getText).toList());
    }

    @Test
    void changeHasNoSetters() {
        assertTrue(Arrays.stream(RefinementRequestedChange.class.getMethods())
                .map(Method::getName).noneMatch(name -> name.startsWith("set")));
        assertTrue(Arrays.stream(RefinementRequestedChange.class.getDeclaredMethods())
                .map(Method::getName).noneMatch(name -> name.startsWith("set")));
    }

    private static RefinementRequest request(UUID briefId, int cycle) {
        return new RefinementRequest(briefId, cycle, RefinementSource.FACT_FINDING, "Please improve.",
                LocalDate.of(2026, 12, 1), "officer-41@example.test", Instant.parse("2026-11-12T09:00:00Z"));
    }
}
