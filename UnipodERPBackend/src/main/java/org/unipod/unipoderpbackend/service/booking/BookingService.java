package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.booking.BookingResponse;
import org.unipod.unipoderpbackend.dto.booking.CreateBookingRequest;
import org.unipod.unipoderpbackend.exception.booking.BookingConflictException;
import org.unipod.unipoderpbackend.exception.booking.BookingNotFoundException;
import org.unipod.unipoderpbackend.exception.booking.FacilityNotFoundException;
import org.unipod.unipoderpbackend.exception.booking.InvalidBookingException;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;
import org.unipod.unipoderpbackend.model.booking.Facility;
import org.unipod.unipoderpbackend.model.booking.FacilityStatus;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.repository.booking.BookingRepository;
import org.unipod.unipoderpbackend.repository.booking.FacilityRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

@Service
public class BookingService {
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");
    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(
            BookingStatus.PENDING,
            BookingStatus.APPROVED,
            BookingStatus.CHECKED_IN
    );

    private final BookingRepository bookings;
    private final FacilityRepository facilities;
    private final NotificationService notifications;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public BookingService(
            BookingRepository bookings,
            FacilityRepository facilities,
            NotificationService notifications,
            AuditTrail auditTrail,
            Clock clock
    ) {
        this.bookings = bookings;
        this.facilities = facilities;
        this.notifications = notifications;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request, String actor) {
        validateTimes(request.startsAt(), request.endsAt());
        Facility facility = facilities.findByIdForUpdate(request.facilityId())
                .orElseThrow(() -> new FacilityNotFoundException(request.facilityId()));
        if (facility.getStatus() == FacilityStatus.MAINTENANCE) {
            throw new BookingConflictException("The facility is under maintenance and cannot be booked.");
        }
        if (request.attendees() > facility.getCapacity()) {
            throw new InvalidBookingException("The group size exceeds the facility capacity.");
        }
        requireNoConflict(facility.getId(), request.startsAt(), request.endsAt(), null);
        Instant now = clock.instant();
        int year = LocalDate.ofInstant(now, KAMPALA).getYear();
        long sequence = bookings.nextReferenceNumber();
        Booking booking = new Booking(
                UUID.randomUUID(),
                "BKG-%d-%06d".formatted(year, sequence),
                facility,
                actor,
                request.purpose().trim(),
                request.startsAt(),
                request.endsAt(),
                request.attendees(),
                clean(request.operator()),
                now
        );
        bookings.saveAndFlush(booking);
        auditTrail.record(actor, "booking.created", "booking", booking.getId(), null,
                AuditTrail.details(
                        "reference", booking.getReference(),
                        "facility", facility.getName(),
                        "status", booking.getStatus().name()
                ));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> forRequester(String requesterId, Pageable pageable) {
        return bookings.findAllByRequesterIdOrderByStartsAtDescIdDesc(requesterId, pageable)
                .map(BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse forRequester(UUID id, String requesterId) {
        return bookings.findByIdAndRequesterId(id, requesterId)
                .map(BookingResponse::from)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> all(BookingStatus status, LocalDate date, Pageable pageable) {
        Page<Booking> page;
        Instant from = date == null ? null : date.atStartOfDay(KAMPALA).toInstant();
        Instant to = date == null ? null : date.plusDays(1).atStartOfDay(KAMPALA).toInstant();
        if (from == null) {
            page = status == null
                    ? bookings.findAllByOrderByStartsAtDescIdDesc(pageable)
                    : bookings.findAllByStatusOrderByStartsAtDescIdDesc(status, pageable);
        } else {
            page = status == null
                    ? bookings.findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
                            from, to, pageable)
                    : bookings.findAllByStatusAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
                            status, from, to, pageable);
        }
        return page.map(BookingResponse::from);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        return bookings.findById(id)
                .map(BookingResponse::from)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    @Transactional
    public BookingResponse approve(UUID id, String actor) {
        Booking booking = lockBooking(id);
        Facility facility = lockFacility(booking);
        requireNotMaintenance(facility);
        requireNoConflict(facility.getId(), booking.getStartsAt(), booking.getEndsAt(), booking.getId());
        booking.approve(clock.instant());
        bookings.saveAndFlush(booking);
        notifyRequester(booking, NotificationType.BOOKING_APPROVED,
                "Booking approved: " + booking.getReference(),
                "Your booking for " + facility.getName() + " has been approved.");
        auditTrail.record(actor, "booking.approved", "booking", id,
                AuditTrail.details("status", BookingStatus.PENDING.name()),
                AuditTrail.details("status", booking.getStatus().name()));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse reject(UUID id, String reason, String actor) {
        String cleanReason = clean(reason);
        if (cleanReason == null || cleanReason.length() > 500) {
            throw new InvalidBookingException("A rejection reason is required.");
        }
        Booking booking = lockBooking(id);
        booking.reject(cleanReason, clock.instant());
        bookings.saveAndFlush(booking);
        notifyRequester(booking, NotificationType.BOOKING_APPROVED,
                "Booking rejected: " + booking.getReference(),
                "Your booking was rejected. Reason: " + cleanReason);
        auditTrail.record(actor, "booking.rejected", "booking", id,
                AuditTrail.details("status", BookingStatus.PENDING.name()),
                AuditTrail.details("status", booking.getStatus().name(), "reason", cleanReason));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse reschedule(UUID id, Instant startsAt, Instant endsAt, String actor) {
        validateTimes(startsAt, endsAt);
        Booking booking = lockBooking(id);
        Facility facility = lockFacility(booking);
        requireNotMaintenance(facility);
        requireNoConflict(facility.getId(), startsAt, endsAt, booking.getId());
        Instant previousStart = booking.getStartsAt();
        booking.reschedule(startsAt, endsAt, clock.instant());
        bookings.saveAndFlush(booking);
        notifyRequester(booking, NotificationType.BOOKING_APPROVED,
                "Booking rescheduled: " + booking.getReference(),
                "Your booking for " + facility.getName() + " has been rescheduled.");
        auditTrail.record(actor, "booking.rescheduled", "booking", id,
                AuditTrail.details("startsAt", previousStart.toString()),
                AuditTrail.details("startsAt", startsAt.toString(), "endsAt", endsAt.toString()));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse checkIn(UUID id, String actor) {
        Booking booking = lockBooking(id);
        booking.checkIn(clock.instant());
        bookings.saveAndFlush(booking);
        auditTrail.record(actor, "booking.checked_in", "booking", id,
                AuditTrail.details("status", BookingStatus.APPROVED.name()),
                AuditTrail.details("status", booking.getStatus().name()));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse checkOut(UUID id, String actor) {
        Booking booking = lockBooking(id);
        booking.checkOut(clock.instant());
        bookings.saveAndFlush(booking);
        auditTrail.record(actor, "booking.checked_out", "booking", id,
                AuditTrail.details("status", BookingStatus.CHECKED_IN.name()),
                AuditTrail.details("status", booking.getStatus().name()));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse cancel(UUID id, String requesterId) {
        Booking booking = bookings.findByIdAndRequesterId(id, requesterId)
                .orElseThrow(() -> new BookingNotFoundException(id));
        booking = lockBooking(booking.getId());
        if (!booking.getRequesterId().equals(requesterId)) {
            throw new BookingNotFoundException(id);
        }
        booking.cancel(clock.instant());
        bookings.saveAndFlush(booking);
        auditTrail.record(requesterId, "booking.cancelled", "booking", id,
                AuditTrail.details("status", booking.getStatus().name()),
                AuditTrail.details("status", BookingStatus.CANCELLED.name()));
        return BookingResponse.from(booking);
    }

    private Booking lockBooking(UUID id) {
        return bookings.findByIdForUpdate(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    private Facility lockFacility(Booking booking) {
        return facilities.findByIdForUpdate(booking.getFacility().getId())
                .orElseThrow(() -> new FacilityNotFoundException(booking.getFacility().getId()));
    }

    private void requireNoConflict(UUID facilityId, Instant startsAt, Instant endsAt, UUID excludeId) {
        boolean conflict = excludeId == null
                ? bookings.existsConflict(facilityId, BLOCKING_STATUSES, startsAt, endsAt)
                : bookings.existsConflictExcluding(
                        facilityId, excludeId, BLOCKING_STATUSES, startsAt, endsAt);
        if (conflict) {
            throw new BookingConflictException("The facility is already booked for that time.");
        }
    }

    private static void requireNotMaintenance(Facility facility) {
        if (facility.getStatus() == FacilityStatus.MAINTENANCE) {
            throw new BookingConflictException("The facility is under maintenance and cannot be booked.");
        }
    }

    private static void validateTimes(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new InvalidBookingException("The booking end time must be after the start time.");
        }
    }

    private void notifyRequester(Booking booking, NotificationType type, String title, String body) {
        String recipient = booking.getRequesterId();
        if (recipient == null || !recipient.contains("@")) {
            return;
        }
        notifications.publish(new NotificationEvent(
                type,
                Set.of(recipient),
                title,
                body,
                "booking",
                booking.getId()
        ));
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
