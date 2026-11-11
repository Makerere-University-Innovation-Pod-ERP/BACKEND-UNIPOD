package org.unipod.unipoderpbackend.repository.factfinding;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;

public interface FactFindingRepository extends JpaRepository<FactFindingCheck, Long> {
    Optional<FactFindingCheck> findTopByBriefIdOrderByRoundDesc(UUID briefId);

    boolean existsByBriefIdAndRoundAndCompletedAtIsNotNull(UUID briefId, int round);

    boolean existsByBriefIdAndCompletedAtIsNotNull(UUID briefId);
}
