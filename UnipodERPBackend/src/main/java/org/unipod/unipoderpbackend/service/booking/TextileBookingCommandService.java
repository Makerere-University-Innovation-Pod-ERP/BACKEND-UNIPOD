package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.booking.TextileBookingResponse;
import org.unipod.unipoderpbackend.exception.booking.AttendeeNotFoundException;
import org.unipod.unipoderpbackend.exception.booking.BookingConflictException;
import org.unipod.unipoderpbackend.exception.booking.BookingNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.BookingAttendee;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;
import org.unipod.unipoderpbackend.model.booking.DepartmentBookingDetail;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.booking.BookingAttendeeRepository;
import org.unipod.unipoderpbackend.repository.booking.DepartmentBookingDetailRepository;
import org.unipod.unipoderpbackend.repository.operations.DepartmentLookup;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;

/**
 * Changes to an existing textile booking: cancelling it and signing people up. Each change first locks the
 * booking's detail row, so two people signing up for the last place, or one cancelling while another signs
 * up, are served one after the other and the second sees the result of the first (D-044). The generic
 * booking endpoints do not take that lock; a clash with them surfaces as a 409 on the booking's version.
 * Audit entries hold ids, statuses and counts only; names and the cancel reason stay out of the log (D-021).
 */
@Service
public class TextileBookingCommandService {
    private static final String BOOKING_ENTITY = "booking";
    private static final String ATTENDEE_ENTITY = "booking_attendee";

    private final BookingService bookings;
    private final DepartmentBookingDetailRepository details;
    private final BookingAttendeeRepository attendees;
    private final TextileBookingViewer viewer;
    private final DepartmentAccess access;
    private final DepartmentLookup departments;
    private final AuditService audit;
    private final Clock clock;

    public TextileBookingCommandService(
            BookingService bookings,
            DepartmentBookingDetailRepository details,
            BookingAttendeeRepository attendees,
            TextileBookingViewer viewer,
            DepartmentAccess access,
            DepartmentLookup departments,
            AuditService audit,
            Clock clock) {
        this.bookings = bookings;
        this.details = details;
        this.attendees = attendees;
        this.viewer = viewer;
        this.access = access;
        this.departments = departments;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public TextileBookingResponse cancel(UUID bookingId, String reason, Actor actor) {
        access.requireManage(actor, DepartmentCode.TEXTILE);
        DepartmentBookingDetail detail = lockTextileDetail(bookingId);
        // Read as a plain value: BookingService locks and loads the booking itself, so no stale copy is kept here.
        BookingStatus before = details.findStatus(DepartmentCode.TEXTILE, bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (before == BookingStatus.CANCELLED) {
            throw new BookingConflictException("This booking is already cancelled.");
        }
        try {
            bookings.cancelAsStaff(bookingId, actor.email());
        } catch (ObjectOptimisticLockingFailureException changedMeanwhile) {
            throw new BookingConflictException("This booking was changed by someone else. Reload it and try again.");
        }
        detail.recordCancellation(reason.strip(), actor.email(), clock.instant());
        details.saveAndFlush(detail);
        audit.log(new AuditEvent(actor, "textile_booking.cancelled", BOOKING_ENTITY, bookingId,
                Map.of("status", before.name()),
                Map.of("status", BookingStatus.CANCELLED.name(), "signedUp", attendees.countByBookingId(bookingId)),
                textileSector()));
        return viewer.view(textileBooking(bookingId));
    }

    @Transactional
    public TextileBookingResponse addAttendee(UUID bookingId, String name, Actor actor) {
        access.requireManage(actor, DepartmentCode.TEXTILE);
        DepartmentBookingDetail detail = lockTextileDetail(bookingId);
        Booking booking = textileBooking(bookingId);
        Instant now = clock.instant();
        if (!detail.takesSignUps()) {
            throw new BookingConflictException("Only training sessions take sign-ups.");
        }
        requireOpen(booking, now);
        long signedUp = attendees.countByBookingId(bookingId);
        if (detail.isFullAt(signedUp)) {
            throw new BookingConflictException("This session is full.");
        }
        BookingAttendee attendee = attendees.saveAndFlush(new BookingAttendee(UUID.randomUUID(), bookingId, name, now));
        audit.log(new AuditEvent(actor, "textile_booking.attendee_added", ATTENDEE_ENTITY, attendee.getId(), null,
                Map.of("bookingId", bookingId.toString(),
                        "signedUp", signedUp + 1,
                        "capacity", detail.getCapacity() == null ? 0 : detail.getCapacity()),
                textileSector()));
        return viewer.view(booking);
    }

    @Transactional
    public TextileBookingResponse setAttendance(UUID bookingId, UUID attendeeId, boolean attended, Actor actor) {
        access.requireManage(actor, DepartmentCode.TEXTILE);
        lockTextileDetail(bookingId);
        Booking booking = textileBooking(bookingId);
        BookingAttendee attendee = attendees.findByIdAndBookingId(attendeeId, bookingId)
                .orElseThrow(AttendeeNotFoundException::new);
        requireNotCancelled(booking);
        boolean before = attendee.isAttended();
        attendee.recordAttendance(attended, clock.instant());
        attendees.saveAndFlush(attendee);
        if (before != attended) {
            audit.log(new AuditEvent(actor, "textile_booking.attendance_recorded", ATTENDEE_ENTITY, attendeeId,
                    Map.of("attended", before),
                    Map.of("attended", attended, "bookingId", bookingId.toString()),
                    textileSector()));
        }
        return viewer.view(booking);
    }

    // Sign-ups close when the session is over, like registration for a training session (D-026).
    private static void requireOpen(Booking booking, Instant now) {
        requireNotCancelled(booking);
        if (!now.isBefore(booking.getEndsAt())) {
            throw new BookingConflictException("Sign-up is closed for this session.");
        }
    }

    private static void requireNotCancelled(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingConflictException("This booking is cancelled.");
        }
    }

    private DepartmentBookingDetail lockTextileDetail(UUID bookingId) {
        return details.findByIdForUpdate(bookingId)
                .filter(detail -> detail.getDepartment() == DepartmentCode.TEXTILE)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private Booking textileBooking(UUID bookingId) {
        return details.findBooking(DepartmentCode.TEXTILE, bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private UUID textileSector() {
        return departments.sectorId(DepartmentCode.TEXTILE).orElse(null);
    }
}
