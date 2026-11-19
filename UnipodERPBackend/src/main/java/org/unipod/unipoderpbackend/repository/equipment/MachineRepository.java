package org.unipod.unipoderpbackend.repository.equipment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.equipment.Machine;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

public interface MachineRepository extends JpaRepository<Machine, UUID> {
    @Query("select m from Machine m where m.department = :department order by lower(m.name)")
    List<Machine> findByDepartmentOrdered(@Param("department") DepartmentCode department);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Machine m where m.id = :id")
    Optional<Machine> findByIdForUpdate(@Param("id") UUID id);

    @Query("select count(m) > 0 from Machine m where m.department = :department and lower(m.name) = lower(:name)")
    boolean existsByDepartmentAndNameIgnoringCase(@Param("department") DepartmentCode department, @Param("name") String name);
}
