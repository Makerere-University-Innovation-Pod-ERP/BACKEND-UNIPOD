package org.unipod.unipoderpbackend.service.report;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.report.CompletionRateResponse;
import org.unipod.unipoderpbackend.dto.report.DepartmentWorkloadResponse;
import org.unipod.unipoderpbackend.dto.report.IncomeTrendResponse;
import org.unipod.unipoderpbackend.dto.report.StageCountsResponse;
import org.unipod.unipoderpbackend.dto.report.StudentVsPublicResponse;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationAction;
import org.unipod.unipoderpbackend.model.authorization.AuthorizationResource;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.report.ReportQueries;
import org.unipod.unipoderpbackend.service.authorization.AuthorizationService;

@Service
public class ReportService {
    private static final AuthorizationResource REPORTS =
            new AuthorizationResource("report", null, null, Set.of(), null);
    private static final Set<SubmissionStage> CLOSED =
            EnumSet.of(SubmissionStage.COMPLETED, SubmissionStage.CLOSED);

    private final ReportQueries queries;
    private final AuthorizationService authorization;
    private final Clock clock;

    public ReportService(ReportQueries queries, AuthorizationService authorization, Clock clock) {
        this.queries = queries;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StageCountsResponse submissionsByStage(Actor actor) {
        authorize(actor);
        Map<String, Long> counts = queries.countByStage();
        Map<SubmissionStage, Long> byStage = new LinkedHashMap<>();
        for (SubmissionStage stage : SubmissionStage.values()) {
            byStage.put(stage, counts.getOrDefault(stage.name(), 0L));
        }
        long drafts = byStage.get(SubmissionStage.DRAFT);
        long all = byStage.values().stream().mapToLong(Long::longValue).sum();
        return new StageCountsResponse(all - drafts, drafts, byStage);
    }

    @Transactional(readOnly = true)
    public StudentVsPublicResponse studentVsPublic(Actor actor) {
        authorize(actor);
        Map<String, Long> counts = queries.countSubmittedByCategory();
        long student = counts.getOrDefault("STUDENT", 0L);
        long general = counts.getOrDefault("GENERAL", 0L);
        long serviceRequests = counts.getOrDefault("SERVICE_REQUEST", 0L);
        return new StudentVsPublicResponse(student, general, serviceRequests, student + general + serviceRequests);
    }

    @Transactional(readOnly = true)
    public CompletionRateResponse completionRate(Actor actor) {
        authorize(actor);
        StageCountsResponse stages = submissionsByStage(actor);
        long submitted = stages.total();
        long completed = stages.byStage().getOrDefault(SubmissionStage.COMPLETED, 0L);
        long closed = CLOSED.stream().mapToLong(stage -> stages.byStage().getOrDefault(stage, 0L)).sum();
        return new CompletionRateResponse(submitted, completed, closed, rate(completed, submitted), rate(closed, submitted));
    }

    @Transactional(readOnly = true)
    public IncomeTrendResponse incomeTrend(Actor actor, int weeks) {
        authorize(actor);
        return incomeTrend(weeks);
    }

    @Transactional(readOnly = true)
    public DepartmentWorkloadResponse departmentWorkload(Actor actor) {
        authorize(actor);
        return new DepartmentWorkloadResponse(queries.departmentWorkload());
    }

    public IncomeTrendResponse incomeTrend(int weeks) {
        ZoneId zone = clock.getZone();
        LocalDate thisWeek = LocalDate.ofInstant(clock.instant(), zone)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = thisWeek.minusWeeks(weeks - 1L);
        Map<LocalDate, Long> totals = new LinkedHashMap<>();
        for (int index = 0; index < weeks; index++) {
            totals.put(first.plusWeeks(index), 0L);
        }
        Instant from = first.atStartOfDay(zone).toInstant();
        for (ReportQueries.PaidAmount paid : queries.paidSince(from)) {
            LocalDate week = LocalDate.ofInstant(paid.paidAt(), zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            totals.computeIfPresent(week, (key, sum) -> sum + paid.amountUgx());
        }
        List<IncomeTrendResponse.Week> series = new ArrayList<>();
        totals.forEach((week, sum) -> series.add(new IncomeTrendResponse.Week(week, sum)));
        return new IncomeTrendResponse(weeks, series.stream().mapToLong(IncomeTrendResponse.Week::incomeUgx).sum(), series);
    }

    private void authorize(Actor actor) {
        authorization.authorize(actor, AuthorizationAction.REPORT_VIEW, REPORTS);
    }

    private static double rate(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round(part * 10000.0 / whole) / 10000.0;
    }
}
