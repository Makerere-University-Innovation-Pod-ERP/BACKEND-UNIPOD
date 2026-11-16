package org.unipod.unipoderpbackend.repository.operations;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.operations.MediaRequest;

public interface MediaRequestRepository extends JpaRepository<MediaRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaRequest m where m.id = :id")
    Optional<MediaRequest> findByIdForUpdate(@Param("id") UUID id);
    @Query(value = "select nextval('media_request_code_seq')", nativeQuery = true)
    long nextCodeSeq();
}

