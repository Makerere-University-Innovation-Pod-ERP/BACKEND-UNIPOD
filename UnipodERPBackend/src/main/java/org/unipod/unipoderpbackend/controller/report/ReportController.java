package org.unipod.unipoderpbackend.controller.report;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.report.CompletionRateResponse;
import org.unipod.unipoderpbackend.dto.report.DepartmentWorkloadResponse;
import org.unipod.unipoderpbackend.dto.report.ExportRequest;
import org.unipod.unipoderpbackend.dto.report.ExportResponse;
import org.unipod.unipoderpbackend.dto.report.IncomeTrendResponse;
import org.unipod.unipoderpbackend.dto.report.StageCountsResponse;
import org.unipod.unipoderpbackend.dto.report.StudentVsPublicResponse;
import org.unipod.unipoderpbackend.model.report.ExportFormat;
import org.unipod.unipoderpbackend.model.report.ReportExport;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.report.ExportService;
import org.unipod.unipoderpbackend.service.report.ReportService;

@RestController
@Validated
public class ReportController {
    private final ReportService reports;
    private final ExportService exports;
    private final CurrentActor currentActor;

    public ReportController(ReportService reports, ExportService exports, CurrentActor currentActor) {
        this.reports = reports;
        this.exports = exports;
        this.currentActor = currentActor;
    }

    @GetMapping("/reports/submissions-by-stage")
    @PreAuthorize("isAuthenticated()")
    public StageCountsResponse submissionsByStage(Authentication authentication) {
        return reports.submissionsByStage(currentActor.from(authentication));
    }

    @GetMapping("/reports/student-vs-public")
    @PreAuthorize("isAuthenticated()")
    public StudentVsPublicResponse studentVsPublic(Authentication authentication) {
        return reports.studentVsPublic(currentActor.from(authentication));
    }

    @GetMapping("/reports/completion-rate")
    @PreAuthorize("isAuthenticated()")
    public CompletionRateResponse completionRate(Authentication authentication) {
        return reports.completionRate(currentActor.from(authentication));
    }

    @GetMapping("/reports/income-trend")
    @PreAuthorize("isAuthenticated()")
    public IncomeTrendResponse incomeTrend(
            @RequestParam(defaultValue = "12") @Min(1) @Max(52) int weeks, Authentication authentication) {
        return reports.incomeTrend(currentActor.from(authentication), weeks);
    }

    @GetMapping("/reports/department-workload")
    @PreAuthorize("isAuthenticated()")
    public DepartmentWorkloadResponse departmentWorkload(Authentication authentication) {
        return reports.departmentWorkload(currentActor.from(authentication));
    }

    @PostMapping("/exports")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ExportResponse export(@Valid @RequestBody ExportRequest request, Authentication authentication) {
        return exports.create(currentActor.from(authentication), request);
    }

    @GetMapping("/exports/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, Authentication authentication) {
        ReportExport export = exports.download(currentActor.from(authentication), id);
        return ResponseEntity.ok()
                .contentType(export.getFormat() == ExportFormat.PDF
                        ? MediaType.APPLICATION_PDF
                        : new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.getFileName()).build().toString())
                .body(export.getContent());
    }
}
