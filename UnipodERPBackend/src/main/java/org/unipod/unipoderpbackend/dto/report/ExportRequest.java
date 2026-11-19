package org.unipod.unipoderpbackend.dto.report;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.unipod.unipoderpbackend.model.report.ExportFormat;
import org.unipod.unipoderpbackend.model.report.ReportType;

public record ExportRequest(
        @NotNull ReportType report,
        @NotNull ExportFormat format,
        @Min(1) @Max(52) Integer weeks
) {
}
