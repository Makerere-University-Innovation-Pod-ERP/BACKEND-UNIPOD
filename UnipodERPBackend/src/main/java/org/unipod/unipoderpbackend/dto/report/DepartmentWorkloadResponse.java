package org.unipod.unipoderpbackend.dto.report;

import java.util.List;
import org.unipod.unipoderpbackend.repository.report.ReportQueries;

public record DepartmentWorkloadResponse(List<ReportQueries.DepartmentLoad> departments) {
}
