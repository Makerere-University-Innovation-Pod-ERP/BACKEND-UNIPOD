package org.unipod.unipoderpbackend.repository.sector;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.sector.Sector;

public interface SectorRepository extends JpaRepository<Sector, UUID> {
    List<Sector> findAllByActiveTrueOrderByNameAsc();

    List<Sector> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select sector from Sector sector where sector.id = :id")
    Optional<Sector> findByIdForShare(@Param("id") UUID id);

    @Query("select count(sector) > 0 from Sector sector where lower(sector.name) = lower(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query("select count(sector) > 0 from Sector sector where lower(sector.name) = lower(:name) and sector.id <> :id")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") UUID id);
}
