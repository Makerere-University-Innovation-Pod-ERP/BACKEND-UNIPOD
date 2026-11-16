package org.unipod.unipoderpbackend.repository.operations;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.operations.MediaRequest;

public interface MediaRequestRepository extends JpaRepository<MediaRequest, UUID> {
    @Query(value = "SELECT nextval('media_request_code_seq')", nativeQuery = true)
    long nextCodeSeq();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaRequest m where m.id = :id")
    Optional<MediaRequest> findByIdForUpdate(@Param("id") UUID id);
    List<MediaRequest> findAllByOrderByUpdatedAtDesc();
    List<MediaRequest> findAllByStatusOrderByUpdatedAtDesc(String status);
    List<MediaRequest> findAllByBriefIdOrderByUpdatedAtDesc(UUID briefId);
    List<MediaRequest> findAllByBriefIdAndStatusOrderByUpdatedAtDesc(UUID briefId, String status);
    boolean existsByBriefIdAndIdNotAndStatusNotIn(UUID briefId, UUID id, Collection<String> statuses);
    List<MediaRequest> findAllBySubmitterIdOrderByUpdatedAtDesc(String submitterId);
    List<MediaRequest> findAllBySubmitterIdAndStatusOrderByUpdatedAtDesc(String submitterId, String status);
    List<MediaRequest> findAllBySubmitterIdAndBriefIdOrderByUpdatedAtDesc(String submitterId, UUID briefId);
    List<MediaRequest> findAllBySubmitterIdAndBriefIdAndStatusOrderByUpdatedAtDesc(
            String submitterId, UUID briefId, String status);
}
