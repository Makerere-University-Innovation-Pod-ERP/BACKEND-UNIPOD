package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.unipod.unipoderpbackend.dto.booking.TimetableEntryResponse;
import org.unipod.unipoderpbackend.service.operations.TimetableService;

/**
 * Turns the dated timetable blocks of a facility into the weekly entries the lab and workshop screens
 * draw (D-043). There is no second timetable: the blocks stay the only record, and a block that repeats
 * every week shows once.
 */
@Component
public class DepartmentTimetable {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final TimetableService timetable;
    private final Clock clock;

    public DepartmentTimetable(TimetableService timetable, Clock clock) {
        this.timetable = timetable;
        this.clock = clock;
    }

    public List<TimetableEntryResponse> weekly(UUID facilityId, CalendarWindow window) {
        Map<String, TimetableEntryResponse> merged = new LinkedHashMap<>();
        timetable.list(facilityId, window.startInstant(clock.getZone()), window.endInstant(clock.getZone()))
                .forEach(block -> {
                    ZonedDateTime starts = block.starts_at().atZone(clock.getZone());
                    ZonedDateTime ends = block.ends_at().atZone(clock.getZone());
                    TimetableEntryResponse entry = new TimetableEntryResponse(
                            block.id().toString(),
                            starts.getDayOfWeek().getValue(),
                            starts.format(TIME),
                            // A block that runs past midnight is drawn to the end of its first day.
                            ends.toLocalDate().isAfter(starts.toLocalDate()) ? "23:59" : ends.format(TIME),
                            block.title());
                    merged.putIfAbsent(
                            entry.weekday() + "|" + entry.start() + "|" + entry.end() + "|" + entry.label(), entry);
                });
        List<TimetableEntryResponse> entries = new ArrayList<>(merged.values());
        entries.sort(Comparator.comparingInt(TimetableEntryResponse::weekday)
                .thenComparing(TimetableEntryResponse::start)
                .thenComparing(TimetableEntryResponse::id));
        return entries;
    }
}
