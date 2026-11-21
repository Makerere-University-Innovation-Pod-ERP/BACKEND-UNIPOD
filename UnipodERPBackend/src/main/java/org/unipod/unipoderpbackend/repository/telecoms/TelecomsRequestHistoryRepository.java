package org.unipod.unipoderpbackend.repository.telecoms;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.telecoms.TelecomsRequestHistory;

public interface TelecomsRequestHistoryRepository extends JpaRepository<TelecomsRequestHistory, UUID> {
    List<TelecomsRequestHistory> findAllByRequestIdInOrderByCreatedAtAscIdAsc(List<UUID> requestIds);
}
