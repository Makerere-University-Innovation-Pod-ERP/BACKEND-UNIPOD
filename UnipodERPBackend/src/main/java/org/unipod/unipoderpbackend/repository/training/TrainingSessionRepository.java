package org.unipod.unipoderpbackend.repository.training;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.training.TrainingSession;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {
    // Matches ix_training_sessions_starts_at: newest session first, id as a stable tie-break.
    @Query("select s from TrainingSession s order by s.startsAt desc, s.id asc")
    List<TrainingSession> findAllNewestFirst();

    // Matches ix_training_sessions_department_starts_at.
    @Query("""
            select s from TrainingSession s
            where s.departmentId = :departmentId
            order by s.startsAt desc, s.id asc
            """)
    List<TrainingSession> findByDepartmentNewestFirst(@Param("departmentId") UUID departmentId);

    // Registering takes this lock so two requests cannot both count the last free seat.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TrainingSession s where s.id = :id")
    Optional<TrainingSession> findByIdForUpdate(@Param("id") UUID id);
}
