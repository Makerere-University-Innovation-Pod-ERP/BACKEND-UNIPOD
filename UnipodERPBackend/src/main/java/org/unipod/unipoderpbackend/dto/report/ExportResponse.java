package org.unipod.unipoderpbackend.dto.report;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.report.ExportFormat;
import org.unipod.unipoderpbackend.model.report.ReportExport;
import org.unipod.unipoderpbackend.model.report.ReportType;

public record ExportResponse(UUID id, ReportType report, ExportFormat format, String fileName, Instant createdAt) {
    public static ExportResponse from(ReportExport export) {
        return new ExportResponse(export.getId(), export.getReport(), export.getFormat(),
                export.getFileName(), export.getCreatedAt());
    }
}
