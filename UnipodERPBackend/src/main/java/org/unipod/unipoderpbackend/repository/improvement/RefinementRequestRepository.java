package org.unipod.unipoderpbackend.repository.improvement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;

public interface RefinementRequestRepository extends JpaRepository<RefinementRequest, Long> {
    Optional<RefinementRequest> findTopByBriefIdOrderByCycleDesc(UUID briefId);

    List<RefinementRequest> findByBriefIdOrderByCycleAsc(UUID briefId);

    boolean existsByBriefIdAndStatus(UUID briefId, RefinementStatus status);

    long countByBriefId(UUID briefId);
}
