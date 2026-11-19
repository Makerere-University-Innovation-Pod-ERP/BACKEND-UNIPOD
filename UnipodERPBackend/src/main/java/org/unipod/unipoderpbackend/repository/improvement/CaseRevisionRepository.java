package org.unipod.unipoderpbackend.repository.improvement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;

public interface CaseRevisionRepository extends JpaRepository<CaseRevision, Long> {
    Optional<CaseRevision> findTopByBriefIdOrderByVersionNumberDesc(UUID briefId);

    List<CaseRevision> findByBriefIdOrderByVersionNumberAsc(UUID briefId);

    Optional<CaseRevision> findByRequestId(Long requestId);

    /**
     * For each of {@code briefIds}, its revision with the highest version number (one row per brief, because
     * (brief_id, version_number) is unique); briefs without a revision are absent. An empty collection returns an
     * empty list without running a query.
     */
    default List<LatestRevision> findLatestForBriefs(Collection<UUID> briefIds) {
        return briefIds.isEmpty() ? List.of() : latestForBriefs(briefIds);
    }

    /** The single query behind {@link #findLatestForBriefs}; call that method instead. */
    @Query("""
            select new org.unipod.unipoderpbackend.repository.improvement.LatestRevision(
                c.briefId, c.id, c.versionNumber)
            from CaseRevision c
            where c.briefId in :briefIds
              and c.versionNumber = (select max(c2.versionNumber) from CaseRevision c2 where c2.briefId = c.briefId)
            """)
    List<LatestRevision> latestForBriefs(@Param("briefIds") Collection<UUID> briefIds);
}
