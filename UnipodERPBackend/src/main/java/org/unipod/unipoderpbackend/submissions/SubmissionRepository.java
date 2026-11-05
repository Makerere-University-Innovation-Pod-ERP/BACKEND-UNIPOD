package org.unipod.unipoderpbackend.submissions;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select submission from Submission submission where submission.id = :id")
    Optional<Submission> findByIdForUpdate(@Param("id") UUID id);

    Page<Submission> findAllByOrderByUpdatedAtDescIdAsc(Pageable pageable);

    Page<Submission> findAllByRegisteredByOrderByUpdatedAtDescIdAsc(String registeredBy, Pageable pageable);

    Page<Submission> findAllByStageOrderByUpdatedAtDescIdAsc(SubmissionStage stage, Pageable pageable);

}
