package org.unipod.unipoderpbackend.repository.training;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.training.TrainingParticipant;

public interface TrainingParticipantRepository extends JpaRepository<TrainingParticipant, UUID> {
    @Query("""
            select p from TrainingParticipant p
            where p.sessionId in :sessionIds
              and p.status <> org.unipod.unipoderpbackend.model.training.RegistrationStatus.CANCELLED
            order by p.sessionId asc, p.registeredAt asc, p.id asc
            """)
    List<TrainingParticipant> findActiveBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);

    @Query("""
            select p from TrainingParticipant p
            where p.sessionId = :sessionId
              and p.status <> org.unipod.unipoderpbackend.model.training.RegistrationStatus.CANCELLED
            order by p.registeredAt asc, p.id asc
            """)
    List<TrainingParticipant> findActiveBySessionId(@Param("sessionId") UUID sessionId);

    @Query("""
            select p from TrainingParticipant p
            where p.id = :participantId
              and p.sessionId = :sessionId
              and p.status <> org.unipod.unipoderpbackend.model.training.RegistrationStatus.CANCELLED
            """)
    Optional<TrainingParticipant> findActive(
            @Param("sessionId") UUID sessionId, @Param("participantId") UUID participantId);

    @Query("""
            select count(p) from TrainingParticipant p
            where p.sessionId = :sessionId
              and p.status <> org.unipod.unipoderpbackend.model.training.RegistrationStatus.CANCELLED
            """)
    long countSeats(@Param("sessionId") UUID sessionId);

    @Query("""
            select count(p) > 0 from TrainingParticipant p
            where p.sessionId = :sessionId
              and lower(p.regNumber) = lower(:regNumber)
              and p.status <> org.unipod.unipoderpbackend.model.training.RegistrationStatus.CANCELLED
            """)
    boolean existsActiveRegNumber(@Param("sessionId") UUID sessionId, @Param("regNumber") String regNumber);

    @Query("""
            select p from TrainingParticipant p
            where p.sessionId = :sessionId and p.id in :ids
            """)
    List<TrainingParticipant> findAllBySessionIdAndIdIn(@Param("sessionId") UUID sessionId, @Param("ids") Collection<UUID> ids);
}
