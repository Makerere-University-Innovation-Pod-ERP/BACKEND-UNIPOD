package org.unipod.unipoderpbackend.repository.report;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.report.ReportExport;

public interface ReportExportRepository extends JpaRepository<ReportExport, UUID> {
}
