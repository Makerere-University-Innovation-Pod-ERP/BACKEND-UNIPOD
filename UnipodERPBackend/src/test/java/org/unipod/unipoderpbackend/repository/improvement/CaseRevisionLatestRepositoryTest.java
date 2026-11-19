package org.unipod.unipoderpbackend.repository.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;

/** CaseRevisionRepository.findLatestForBriefs: one query for the highest revision of each brief in a set. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class CaseRevisionLatestRepositoryTest {
    private static final Instant AT = Instant.parse("2026-11-20T10:00:00Z");

    @Autowired
    private CaseRevisionRepository revisions;

    @Autowired
    private RefinementRequestRepository requests;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void aBriefWithRevisionsTwoAndThreeReturnsRevisionThreeWithItsId() {
        UUID brief = brief(1);
        revision(brief, 1, 2);
        CaseRevision three = revision(brief, 2, 3);

        List<LatestRevision> latest = latest(brief);

        assertEquals(List.of(new LatestRevision(brief, three.getId(), 3)), latest);
    }

    @Test
    void aBriefWithOneRevisionReturnsIt() {
        UUID brief = brief(2);
        CaseRevision only = revision(brief, 1, 2);

        assertEquals(List.of(new LatestRevision(brief, only.getId(), 2)), latest(brief));
    }

    @Test
    void aBriefWithoutRevisionsReturnsNothing() {
        UUID brief = brief(3);
        request(brief, 1);

        assertTrue(latest(brief).isEmpty());
    }

    @Test
    void severalBriefsInOneCallEachGetTheirOwnLatest() {
        UUID a = brief(4);
        revision(a, 1, 2);
        CaseRevision aLatest = revision(a, 2, 3);
        CaseRevision aNewest = revision(a, 3, 4);
        UUID b = brief(5);
        CaseRevision bOnly = revision(b, 1, 2);
        UUID none = brief(6);
        UUID other = brief(7);
        revision(other, 1, 2);

        List<LatestRevision> latest = latest(a, b, none).stream()
                .sorted(Comparator.comparing(LatestRevision::versionNumber)).toList();

        assertEquals(List.of(new LatestRevision(b, bOnly.getId(), 2), new LatestRevision(a, aNewest.getId(), 4)),
                latest);
        assertTrue(latest.stream().noneMatch(row -> row.revisionId().equals(aLatest.getId())));
        assertTrue(latest.stream().noneMatch(row -> row.briefId().equals(other)));
    }

    @Test
    void anEmptyCollectionReturnsAnEmptyListWithoutError() {
        assertEquals(List.of(), revisions.findLatestForBriefs(Set.of()));
        assertEquals(List.of(), revisions.findLatestForBriefs(List.of()));
    }

    private List<LatestRevision> latest(UUID... briefs) {
        entityManager.flush();
        entityManager.clear();
        return revisions.findLatestForBriefs(List.of(briefs));
    }

    private static UUID brief(long n) {
        return new UUID(0x1A7E57L, n);
    }

    private RefinementRequest request(UUID brief, int cycle) {
        RefinementRequest request = new RefinementRequest(brief, cycle, RefinementSource.COORDINATOR,
                "Improve cycle " + cycle, LocalDate.of(2026, 12, 1), "coordinator@example.test", AT);
        request.setStatus(RefinementStatus.RESPONDED);
        request.setRespondedAt(AT.plusSeconds(cycle));
        return requests.saveAndFlush(request);
    }

    /** A revision answering a new request of {@code cycle} (case_revisions references its request and brief). */
    private CaseRevision revision(UUID brief, int cycle, int versionNumber) {
        RefinementRequest request = request(brief, cycle);
        return revisions.saveAndFlush(new CaseRevision(brief, versionNumber, request.getId(), "Changed " + cycle,
                "submitter@example.test", AT.plusSeconds(versionNumber)));
    }
}
