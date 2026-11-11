package org.unipod.unipoderpbackend.repository.factfinding;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;

public interface FactFindingRepository extends JpaRepository<FactFindingCheck, Long> {
    Optional<FactFindingCheck> findTopByBriefIdOrderByRoundDesc(Long briefId);

    boolean existsByBriefIdAndRoundAndCompletedAtIsNotNull(Long briefId, int round);
}
