package org.unipod.unipoderpbackend.service.finance;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse.NeedsAttentionCase;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardPipelineStage;
import org.unipod.unipoderpbackend.dto.finance.FinanceOverviewResponse;
import org.unipod.unipoderpbackend.dto.finance.FinanceOverviewResponse.AttentionItem;
import org.unipod.unipoderpbackend.dto.finance.FinanceOverviewResponse.Facility;
import org.unipod.unipoderpbackend.dto.finance.FinanceOverviewResponse.Income;
import org.unipod.unipoderpbackend.dto.finance.FinanceOverviewResponse.Metric;
import org.unipod.unipoderpbackend.dto.finance.FinanceOverviewResponse.PipelineStage;
import org.unipod.unipoderpbackend.dto.finance.FinanceOverviewResponse.RecentActivity;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;
import org.unipod.unipoderpbackend.model.finance.InvoiceStatus;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.auth.UserProfileRepository;
import org.unipod.unipoderpbackend.repository.booking.BookingRepository;
import org.unipod.unipoderpbackend.repository.booking.FacilityRepository;
import org.unipod.unipoderpbackend.repository.dashboard.PipelineMetricsRepository;
import org.unipod.unipoderpbackend.repository.finance.InvoiceRepository;
import org.unipod.unipoderpbackend.repository.report.ReportQueries;

@Service
public class FinanceOverviewService {
    private static final String CURRENCY = "UGX";
    private static final Duration STALE_AFTER = Duration.ofDays(14);
    private static final DateTimeFormatter ACTIVITY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneId.of("UTC"));
    private static final List<BookingStatus> ACTIVE_BOOKINGS = List.of(
            BookingStatus.PENDING, BookingStatus.APPROVED, BookingStatus.CHECKED_IN, BookingStatus.OUT);

    private final InvoiceRepository invoices;
    private final PipelineMetricsRepository pipelineMetrics;
    private final ReportQueries reportQueries;
    private final BookingRepository bookings;
    private final FacilityRepository facilities;
    private final AuditEntryRepository auditEntries;
    private final UserProfileRepository profiles;
    private final ApiUserRepository users;
    private final Clock clock;

    public FinanceOverviewService(
            InvoiceRepository invoices,
            PipelineMetricsRepository pipelineMetrics,
            ReportQueries reportQueries,
            BookingRepository bookings,
            FacilityRepository facilities,
            AuditEntryRepository auditEntries,
            UserProfileRepository profiles,
            ApiUserRepository users,
            Clock clock
    ) {
        this.invoices = invoices;
        this.pipelineMetrics = pipelineMetrics;
        this.reportQueries = reportQueries;
        this.bookings = bookings;
        this.facilities = facilities;
        this.auditEntries = auditEntries;
        this.profiles = profiles;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FinanceOverviewResponse overview(Actor actor) {
        Instant now = clock.instant();
        ZoneId zone = clock.getZone();
        DashboardOverviewResponse pipeline = pipelineMetrics.overview(monthStart(now, zone));
        List<NeedsAttentionCase> attentionCases = pipelineMetrics.needsAttention(now.minus(STALE_AFTER));

        long pendingCount = invoices.countByStatus(InvoiceStatus.WAITING_FOR_PROOF)
                + invoices.countByStatus(InvoiceStatus.WAITING_FOR_CHECK);
        long pendingAmount = invoices.sumAmountByStatusIn(
                List.of(InvoiceStatus.WAITING_FOR_PROOF, InvoiceStatus.WAITING_FOR_CHECK));
        long collectedMonth = collectedThisMonth(now, zone);

        return new FinanceOverviewResponse(
                "FINANCE OFFICER",
                greeting(actor, now, zone),
                "Here's what's moving across UniPod operations today.",
                metrics(pipeline, pendingCount, pendingAmount, collectedMonth),
                pipelineStages(pipeline.pipeline()),
                needsAttention(attentionCases),
                income(now, zone),
                facilities(now),
                recentActivities()
        );
    }

    private List<Metric> metrics(DashboardOverviewResponse pipeline, long pendingCount, long pendingAmount, long collectedMonth) {
        return List.of(
                new Metric("m-pending", "Invoices pending", pendingCount, formatUgx(pendingAmount), "default", "FileText"),
                new Metric("m-collected", "Collected this month", formatUgx(collectedMonth), "Paid + receipted", "forest", "DollarSign"),
                new Metric("m-review", "Awaiting review", pipeline.waitingForReview(), "Pipeline submissions", "default", "AlertCircle"),
                new Metric("m-completed", "Completed this month", pipeline.completedThisMonth(), "Closed successfully", "default", "TrendingUp")
        );
    }

    private List<PipelineStage> pipelineStages(Map<DashboardPipelineStage, Long> pipeline) {
        List<PipelineStage> stages = new ArrayList<>();
        for (DashboardPipelineStage stage : DashboardPipelineStage.values()) {
            stages.add(new PipelineStage(stage.name(), label(stage), pipeline.getOrDefault(stage, 0L)));
        }
        return stages;
    }

    private List<AttentionItem> needsAttention(List<NeedsAttentionCase> cases) {
        List<AttentionItem> items = new ArrayList<>();
        int index = 1;
        for (NeedsAttentionCase item : cases) {
            items.add(new AttentionItem(
                    item.id().toString(),
                    String.format("%02d", index++),
                    item.title(),
                    item.reason(),
                    null,
                    item.stage() == null ? null : item.stage().name(),
                    item.code()
            ));
        }
        return items;
    }

    private Income income(Instant now, ZoneId zone) {
        LocalDate thisWeek = LocalDate.ofInstant(now, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = thisWeek.minusWeeks(6);
        Map<LocalDate, Long> totals = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            totals.put(first.plusWeeks(i), 0L);
        }
        Instant from = first.atStartOfDay(zone).toInstant();
        for (ReportQueries.PaidAmount paid : reportQueries.paidSince(from)) {
            LocalDate week = LocalDate.ofInstant(paid.paidAt(), zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            totals.computeIfPresent(week, (k, sum) -> sum + paid.amountUgx());
        }
        long monthAmount = collectedThisMonth(now, zone);
        List<Income.Bar> bars = new ArrayList<>();
        int weekNo = 1;
        for (Map.Entry<LocalDate, Long> entry : totals.entrySet()) {
            boolean current = entry.getKey().equals(thisWeek);
            bars.add(new Income.Bar(current ? "Current" : "W" + weekNo, entry.getValue(), current ? "#d7ef67" : "#1b5e43"));
            weekNo++;
        }
        return new Income(formatUgx(monthAmount), monthAmount, CURRENCY, "Month to date", bars);
    }

    private List<Facility> facilities(Instant now) {
        List<org.unipod.unipoderpbackend.model.booking.Facility> all = facilities.findAll();
        List<Facility> result = new ArrayList<>();
        for (org.unipod.unipoderpbackend.model.booking.Facility facility : all) {
            long activeBookings = countActiveBookings(facility.getId(), now);
            long capacity = Math.max(facility.getCapacity(), 0);
            long percentage = capacity == 0 ? 0 : Math.min(100, Math.round(activeBookings * 100.0 / capacity));
            result.add(new Facility(
                    facility.getId().toString(),
                    facility.getName(),
                    activeBookings,
                    capacity,
                    percentage
            ));
        }
        return result;
    }

    private long countActiveBookings(UUID facilityId, Instant now) {
        return bookings.findAllByOrderByStartsAtDescIdDesc(PageRequest.of(0, 200)).stream()
                .filter(b -> b.getFacility() != null && facilityId.equals(b.getFacility().getId()))
                .filter(b -> ACTIVE_BOOKINGS.contains(b.getStatus()))
                .filter(b -> b.getEndsAt() != null && b.getEndsAt().isAfter(now))
                .count();
    }

    private List<RecentActivity> recentActivities() {
        List<AuditEntry> entries = auditEntries.search(null, null, null, null, Instant.EPOCH,
                Instant.parse("9999-12-31T00:00:00Z"), PageRequest.of(0, 10)).getContent();
        List<RecentActivity> items = new ArrayList<>();
        for (AuditEntry entry : entries) {
            items.add(new RecentActivity(
                    entry.getId().toString(),
                    actorName(entry.getActor()),
                    humanizeAction(entry.getAction()),
                    humanizeEntity(entry.getEntityType()),
                    null,
                    ACTIVITY_TIME.format(entry.getOccurredAt())
            ));
        }
        return items;
    }

    private String actorName(String email) {
        if (email == null || email.isBlank()) {
            return "System";
        }
        return users.findByEmailIgnoreCase(email)
                .flatMap(user -> profiles.findById(user.getId()))
                .map(profile -> {
                    String name = profile.getDisplayFirstName();
                    if (name == null || name.isBlank()) {
                        name = profile.getFullName();
                    }
                    return (name == null || name.isBlank()) ? email : name;
                })
                .orElse(email);
    }

    private static String humanizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "updated";
        }
        return action.replace('.', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }

    private static String humanizeEntity(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return "record";
        }
        return entityType.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String greeting(Actor actor, Instant now, ZoneId zone) {
        int hour = ZonedDateTime.ofInstant(now, zone).getHour();
        String salutation = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        String name = users.findByEmailIgnoreCase(actor.email())
                .flatMap(user -> profiles.findById(user.getId()))
                .map(profile -> {
                    String n = profile.getDisplayFirstName();
                    if (n == null || n.isBlank()) {
                        n = profile.getFullName();
                    }
                    return n;
                })
                .orElse(actor.email());
        if (name == null || name.isBlank()) {
            name = "there";
        }
        return salutation + ", " + name + ".";
    }

    private long collectedThisMonth(Instant now, ZoneId zone) {
        long total = 0L;
        for (ReportQueries.PaidAmount paid : reportQueries.paidSince(monthStart(now, zone))) {
            total += paid.amountUgx();
        }
        return total;
    }

    private static Instant monthStart(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone).withDayOfMonth(1).atStartOfDay(zone).toInstant();
    }

    private static String formatUgx(long amount) {
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
        fmt.setGroupingUsed(true);
        return CURRENCY + " " + fmt.format(amount);
    }

    private static String label(DashboardPipelineStage stage) {
        return switch (stage) {
            case INTAKE -> "Intake";
            case FACT_FINDING -> "Fact-finding";
            case UNDER_REVIEW -> "Under review";
            case REFINEMENT_REQUESTED -> "Refinement requested";
            case FURTHER_HANDLING -> "Further handling";
            case COMPLETED -> "Completed";
        };
    }
}
