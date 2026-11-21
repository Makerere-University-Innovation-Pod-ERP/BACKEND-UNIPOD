package org.unipod.unipoderpbackend.repository.telecoms;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.telecoms.TelecomsRequest;

public interface TelecomsRequestRepository extends JpaRepository<TelecomsRequest, UUID> {
    @Query(value = "select nextval('telecoms_request_code_seq')", nativeQuery = true)
    long nextCodeSeq();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from TelecomsRequest request where request.id = :id")
    Optional<TelecomsRequest> findByIdForUpdate(@Param("id") UUID id);

    Page<TelecomsRequest> findAll(Pageable pageable);
}
