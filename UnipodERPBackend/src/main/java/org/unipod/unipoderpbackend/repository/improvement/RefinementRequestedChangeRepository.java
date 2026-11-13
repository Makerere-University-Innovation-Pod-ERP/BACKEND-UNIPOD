package org.unipod.unipoderpbackend.repository.improvement;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequestedChange;

public interface RefinementRequestedChangeRepository extends JpaRepository<RefinementRequestedChange, Long> {
    List<RefinementRequestedChange> findByRequestIdOrderByPositionAsc(Long requestId);

    List<RefinementRequestedChange> findByRequestIdInOrderByRequestIdAscPositionAsc(Collection<Long> requestIds);
}
