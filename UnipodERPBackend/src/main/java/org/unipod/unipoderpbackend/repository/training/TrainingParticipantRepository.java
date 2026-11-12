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
    // One query for every listed session, so the overview never loads participants per session.
    @Query("""
            select p from TrainingParticipant p
            where p.sessionId in :sessionIds
              and p.status <> org.unipod.unipoderpbackend.model.training.RegistrationStatus.CANCELLED
            order by p.sessionId asc, p.registeredAt asc, p.id asc
            """)
    List<TrainingParticipant> findActiveBySessionIds(@Param("sessionIds") Collection<UUID> sessionIds);

    // A cancelled registration counts as gone, so it is never found here (D-023).
    @Query("""
            select p from TrainingParticipant p
            where p.id = :participantId
              and p.sessionId = :sessionId
              and p.status <> org.unipod.unipoderpbackend.model.training.RegistrationStatus.CANCELLED
            """)
    Optional<TrainingParticipant> findActive(
            @Param("sessionId") UUID sessionId, @Param("participantId") UUID participantId);

    // Seats are held by PENDING_PAYMENT and CONFIRMED registrations (D-022).
    @Query("""
            select count(p) from TrainingParticipant p
            where p.sessionId = :sessionId
              and p.status <> org.unipod.unipoderpbackend.model.training.RegistrationStatus.CANCELLED
            """)
    long countSeats(@Param("sessionId") UUID sessionId);

    // Same expression as uq_training_participants_session_reg_number, so the lookup uses that index.
    @Query("""
            select count(p) > 0 from TrainingParticipant p
            where p.sessionId = :sessionId
              and lower(p.regNumber) = lower(:regNumber)
              and p.status <> org.unipod.unipoderpbackend.model.training.RegistrationStatus.CANCELLED
            """)
    boolean existsActiveRegNumber(@Param("sessionId") UUID sessionId, @Param("regNumber") String regNumber);
}
