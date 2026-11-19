package org.unipod.unipoderpbackend.dto.equipment;

import java.time.Instant;
import java.util.List;
import org.unipod.unipoderpbackend.model.equipment.FaultKind;
import org.unipod.unipoderpbackend.model.equipment.FaultReport;

/** What the textile "report a fault" screen gets back (`services/textile.js`, faultReportResultSchema). */
public record MachineFaultReportResponse(MachineResponse machine, Report report, List<String> managersNotified) {

    public record Report(String id, FaultKind kind, String description, Photo photo, String by, Instant at) {
        static Report from(FaultReport report) {
            Photo photo = report.getPhotoName() == null ? null : new Photo(report.getPhotoName());
            return new Report(report.getId().toString(), report.getKind(), report.getDescription(), photo,
                    report.getReportedBy(), report.getReportedAt());
        }
    }

    public record Photo(String name) {
    }

    public static MachineFaultReportResponse of(MachineResponse machine, FaultReport report, List<String> managersNotified) {
        return new MachineFaultReportResponse(machine, Report.from(report), managersNotified);
    }
}
