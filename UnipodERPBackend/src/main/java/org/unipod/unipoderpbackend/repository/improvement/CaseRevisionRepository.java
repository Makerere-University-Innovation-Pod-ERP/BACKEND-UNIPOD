package org.unipod.unipoderpbackend.repository.improvement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;

public interface CaseRevisionRepository extends JpaRepository<CaseRevision, Long> {
    Optional<CaseRevision> findTopByBriefIdOrderByVersionNumberDesc(UUID briefId);

    List<CaseRevision> findByBriefIdOrderByVersionNumberAsc(UUID briefId);

    Optional<CaseRevision> findByRequestId(Long requestId);
}
