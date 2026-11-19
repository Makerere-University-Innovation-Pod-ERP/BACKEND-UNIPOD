package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.booking.BookingResponse;
import org.unipod.unipoderpbackend.dto.booking.CreateBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.CreateTextileBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.TextileBookingResponse;
import org.unipod.unipoderpbackend.dto.booking.TextileBookingStatus;
import org.unipod.unipoderpbackend.dto.booking.TextileCalendarResponse;
import org.unipod.unipoderpbackend.dto.booking.TextileKind;
import org.unipod.unipoderpbackend.dto.booking.TimetableEntryResponse;
import org.unipod.unipoderpbackend.exception.booking.InvalidBookingException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.DepartmentBookingDetail;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.booking.DepartmentBookingDetailRepository;
import org.unipod.unipoderpbackend.repository.operations.DepartmentLookup;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;

/**
 * The textile workshop calendar and new bookings. As for the lab, the booking goes through
 * {@link BookingService}, so the facility lock, the clash rules and the timetable apply (409). The audit
 * entry holds ids, kind, times and counts only (D-021).
 */
@Service
public class TextileBookingService {
    private final BookingService bookings;
    private final DepartmentBookingDetailRepository details;
    private final TextileBookingViewer viewer;
    private final DepartmentTimetable timetable;
    private final DepartmentAccess access;
    private final DepartmentLookup departments;
    private final AuditService audit;
    private final Clock clock;

    public TextileBookingService(
            BookingService bookings,
            DepartmentBookingDetailRepository details,
            TextileBookingViewer viewer,
            DepartmentTimetable timetable,
            DepartmentAccess access,
            DepartmentLookup departments,
            AuditService audit,
            Clock clock) {
        this.bookings = bookings;
        this.details = details;
        this.viewer = viewer;
        this.timetable = timetable;
        this.access = access;
        this.departments = departments;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TextileCalendarResponse calendar(LocalDate from, LocalDate to) {
        CalendarWindow window = CalendarWindow.of(from, to, LocalDate.now(clock));
        List<Booking> found = details.findInWindow(
                DepartmentCode.TEXTILE, window.startInstant(clock.getZone()), window.endInstant(clock.getZone()),
                TextileBookingStatus.LISTED);
        Optional<UUID> facility = departments.facilityId(DepartmentCode.TEXTILE);
        List<TimetableEntryResponse> classes = facility.map(id -> timetable.weekly(id, window)).orElse(List.of());
        return new TextileCalendarResponse(viewer.views(found), classes);
    }

    @Transactional
    public TextileBookingResponse create(CreateTextileBookingRequest request, Actor actor) {
        access.requireManage(actor, DepartmentCode.TEXTILE);
        if (request.kind() == TextileKind.WORKSHOP && request.capacity() != null) {
            throw new InvalidBookingException("Only a training session has a number of places.");
        }
        UUID facility = access.requireFacility(DepartmentCode.TEXTILE);
        Instant starts = request.date().atTime(request.start()).atZone(clock.getZone()).toInstant();
        Instant ends = request.date().atTime(request.end()).atZone(clock.getZone()).toInstant();
        int expected = request.capacity() == null ? 1 : request.capacity();

        BookingResponse created = bookings.createApproved(new CreateBookingRequest(
                facility, request.purpose().strip(), starts, ends, expected, null, null, null, null, null),
                actor.email());

        details.saveAndFlush(new DepartmentBookingDetail(
                created.id(), DepartmentCode.TEXTILE, request.kind().toBookingKind(), actor.email(), null,
                request.machineId(), request.capacity(), clock.instant()));
        audit.log(new AuditEvent(actor, "textile_booking.created", "booking", created.id(), null,
                Map.of("facilityId", facility.toString(),
                        "kind", request.kind().name(),
                        "status", created.status().name(),
                        "startsAt", starts.toString(),
                        "endsAt", ends.toString(),
                        "capacity", request.capacity() == null ? 0 : request.capacity()),
                departments.sectorId(DepartmentCode.TEXTILE).orElse(null)));

        Booking booking = details.findBooking(DepartmentCode.TEXTILE, created.id()).orElseThrow();
        return viewer.view(booking);
    }
}
