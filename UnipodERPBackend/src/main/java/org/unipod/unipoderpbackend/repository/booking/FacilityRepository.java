package org.unipod.unipoderpbackend.repository.booking;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.booking.Facility;

public interface FacilityRepository extends JpaRepository<Facility, UUID> {
    boolean existsByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select facility from Facility facility where facility.id = :id")
    Optional<Facility> findByIdForUpdate(@Param("id") UUID id);
}
