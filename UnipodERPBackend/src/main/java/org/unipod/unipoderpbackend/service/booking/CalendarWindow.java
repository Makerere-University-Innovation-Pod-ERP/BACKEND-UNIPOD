package org.unipod.unipoderpbackend.service.booking;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;

/**
 * The inclusive range of Kampala dates a department calendar shows. With no dates it is the current
 * week, Monday to Sunday; with one date the other end is a week away.
 */
public record CalendarWindow(LocalDate from, LocalDate to) {
    // Three months of dates, both ends included.
    private static final long LONGEST_DAYS = 92;

    public CalendarWindow {
        if (to.isBefore(from)) {
            throw new InvalidOperationsRequestException("The end date must not be before the start date.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= LONGEST_DAYS) {
            throw new InvalidOperationsRequestException("Ask for at most three months at a time.");
        }
    }

    public static CalendarWindow of(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate start = from != null ? from
                : to != null ? to.minusDays(6)
                : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = to != null ? to : start.plusDays(6);
        return new CalendarWindow(start, end);
    }

    public Instant startInstant(ZoneId zone) {
        return from.atStartOfDay(zone).toInstant();
    }

    /** The first instant after the last day. */
    public Instant endInstant(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }
}
