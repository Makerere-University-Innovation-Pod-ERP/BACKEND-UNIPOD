package org.unipod.unipoderpbackend.repository.equipment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.equipment.FaultReport;

public interface FaultReportRepository extends JpaRepository<FaultReport, UUID> {
}
