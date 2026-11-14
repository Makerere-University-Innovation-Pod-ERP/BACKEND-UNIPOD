package org.unipod.unipoderpbackend.service.report;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.report.CompletionRateResponse;
import org.unipod.unipoderpbackend.dto.report.ExportRequest;
import org.unipod.unipoderpbackend.dto.report.ExportResponse;
import org.unipod.unipoderpbackend.dto.report.StudentVsPublicResponse;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.report.ReportExport;
import org.unipod.unipoderpbackend.repository.report.ReportExportRepository;

@Service
public class ExportService {
    private final ReportService reports;
    private final ReportExportRepository exports;
    private final Clock clock;

    public ExportService(ReportService reports, ReportExportRepository exports, Clock clock) {
        this.reports = reports;
        this.exports = exports;
        this.clock = clock;
    }

    @Transactional
    public ExportResponse create(Actor actor, ExportRequest request) {
        String csv = switch (request.report()) {
            case SUBMISSIONS_BY_STAGE -> csv(List.of(List.of("stage", "count")),
                    reports.submissionsByStage(actor).byStage().entrySet().stream()
                            .map(entry -> List.of(entry.getKey().name(), String.valueOf(entry.getValue()))).toList());
            case STUDENT_VS_PUBLIC -> {
                StudentVsPublicResponse split = reports.studentVsPublic(actor);
                yield csv(List.of(List.of("category", "count")), List.of(
                        List.of("STUDENT", String.valueOf(split.student())),
                        List.of("GENERAL", String.valueOf(split.general())),
                        List.of("SERVICE_REQUEST", String.valueOf(split.serviceRequests()))));
            }
            case COMPLETION_RATE -> {
                CompletionRateResponse rate = reports.completionRate(actor);
                yield csv(List.of(List.of("submitted", "completed", "closed", "completion_rate", "closed_rate")), List.of(
                        List.of(String.valueOf(rate.submitted()), String.valueOf(rate.completed()),
                                String.valueOf(rate.closed()), String.valueOf(rate.completionRate()),
                                String.valueOf(rate.closedRate()))));
            }
            case INCOME_TREND -> csv(List.of(List.of("week_start", "income_ugx")),
                    reports.incomeTrend(actor, request.weeks() == null ? 12 : request.weeks()).series().stream()
                            .map(week -> List.of(week.weekStart().toString(), String.valueOf(week.incomeUgx()))).toList());
            case DEPARTMENT_WORKLOAD -> csv(List.of(List.of("department", "open_cases", "open_invoices")),
                    reports.departmentWorkload(actor).departments().stream()
                            .map(row -> List.of(row.departmentName(), String.valueOf(row.openCases()),
                                    String.valueOf(row.openInvoices()))).toList());
        };
        Instant now = clock.instant();
        String fileName = "%s-%s.csv".formatted(
                request.report().name().toLowerCase(Locale.ROOT).replace('_', '-'), LocalDate.ofInstant(now, clock.getZone()));
        ReportExport export = exports.save(new ReportExport(UUID.randomUUID(), request.report(), request.format(),
                actor.email(), fileName, csv.getBytes(StandardCharsets.UTF_8), now));
        return ExportResponse.from(export);
    }

    @Transactional(readOnly = true)
    public ReportExport download(Actor actor, UUID id) {
        return exports.findById(id)
                .filter(export -> export.getRequestedBy().equals(actor.email()) || actor.role() == ApiUserRole.ADMINISTRATOR)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The export was not found."));
    }

    private static String csv(List<List<String>> header, List<List<String>> rows) {
        return Stream.concat(header.stream(), rows.stream())
                .map(row -> row.stream().map(ExportService::cell).collect(Collectors.joining(",")))
                .collect(Collectors.joining("\r\n", "", "\r\n"));
    }

    static String cell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0 && !safe.matches("-?\\d+(\\.\\d+)?")) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
