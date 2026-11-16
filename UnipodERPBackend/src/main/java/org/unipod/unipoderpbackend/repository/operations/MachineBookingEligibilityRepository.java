package org.unipod.unipoderpbackend.repository.operations;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.operations.MachineBookingEligibility;

public interface MachineBookingEligibilityRepository extends JpaRepository<MachineBookingEligibility, UUID> {
    boolean existsByUserIdAndDepartmentIgnoreCase(String userId, String department);
    Optional<MachineBookingEligibility> findByUserIdAndDepartmentIgnoreCase(String userId, String department);
}

