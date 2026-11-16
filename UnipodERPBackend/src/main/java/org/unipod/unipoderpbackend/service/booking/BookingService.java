package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
import org.unipod.unipoderpbackend.repository.operations.TimetableBlockRepository;
import org.unipod.unipoderpbackend.repository.operations.MachineBookingEligibilityRepository;

@Service
public class BookingService {
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");
    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(
            BookingStatus.PENDING,
            BookingStatus.APPROVED,
            BookingStatus.CHECKED_IN,
            BookingStatus.OUT
    );

    private final BookingRepository bookings;
    private final FacilityRepository facilities;
    private final NotificationService notifications;
    private final AuditTrail auditTrail;
    private final TimetableBlockRepository timetable;
    private final MachineBookingEligibilityRepository eligibilities;
    private final Clock clock;

    public BookingService(
            BookingRepository bookings,
            FacilityRepository facilities,
            NotificationService notifications,
            AuditTrail auditTrail,
            TimetableBlockRepository timetable,
            MachineBookingEligibilityRepository eligibilities,
            Clock clock
    ) {
        this.bookings = bookings;
        this.facilities = facilities;
        this.notifications = notifications;
        this.auditTrail = auditTrail;
        this.timetable = timetable;
        this.eligibilities = eligibilities;
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
        requireMachineEligibilityIfNeeded(facility, actor);
        requireNoConflict(facility.getId(), request.startsAt(), request.endsAt(), null);
        requireNoTimetableClash(facility.getId(), request.startsAt(), request.endsAt());
        Instant now = clock.instant();
        int year = LocalDate.ofInstant(now, KAMPALA).getYear();
        long sequence = bookings.nextReferenceNumber();
        String requestedType = request.requestType() == null || request.requestType().isBlank()
                ? "FACILITY" : request.requestType().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("FACILITY", "EQUIPMENT", "ALLOCATION").contains(requestedType)) {
            throw new InvalidBookingException("requestType must be FACILITY or EQUIPMENT.");
        }
        boolean equipment = Boolean.TRUE.equals(request.isEquipmentRequest())
                || "EQUIPMENT".equals(requestedType) || "ALLOCATION".equals(requestedType);
        String reqType = equipment ? "EQUIPMENT" : "FACILITY";
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
                reqType,
                equipment,
                clean(request.equipmentName()),
                clean(request.purposeDetail()),
                now
        );
        bookings.saveAndFlush(booking);
        auditTrail.record(actor, "booking.created", "booking", booking.getId(), null,
                AuditTrail.details(
                        "reference", booking.getReference(),
                        "facility", facility.getName(),
                        "status", booking.getStatus().name(),
                        "requestType", booking.getRequestType()
                ));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> forRequester(String requesterId, Pageable pageable) {
        return bookings.findAllByRequesterIdOrderByStartsAtDescIdDesc(requesterId, pageable)
                .map(BookingResponse::forSubmitter);
    }

    @Transactional(readOnly = true)
    public BookingResponse forRequester(UUID id, String requesterId) {
        return bookings.findByIdAndRequesterId(id, requesterId)
                .map(BookingResponse::forSubmitter)
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
        return decide(id, "APPROVED", null, actor);
    }

    @Transactional
    public BookingResponse reject(UUID id, String reason, String actor) {
        return decide(id, "DECLINED", reason, actor);
    }

    @Transactional
    public BookingResponse decide(UUID id, String decision, String note, String actor) {
        Booking booking = lockBooking(id);
        Facility facility = lockFacility(booking);
        String d = decision == null ? "" : decision.trim().toUpperCase();
        if ("APPROVED".equals(d)) {
            requireNotMaintenance(facility);
            requireMachineEligibilityIfNeeded(facility, booking.getRequesterId());
            requireNoConflict(facility.getId(), booking.getStartsAt(), booking.getEndsAt(), booking.getId());
            requireNoTimetableClash(facility.getId(), booking.getStartsAt(), booking.getEndsAt());
            if (booking.isEquipmentRequest() || "EQUIPMENT".equalsIgnoreCase(booking.getRequestType()) || "ALLOCATION".equalsIgnoreCase(booking.getRequestType())) {
                booking.approveOut(clean(note), actor, "WORKING", clock.instant());
            } else {
                booking.approve(clean(note), actor, clock.instant());
            }
            bookings.saveAndFlush(booking);
            notifyRequester(booking, NotificationType.BOOKING_APPROVED,
                    "Booking approved: " + booking.getReference(),
                    "Your booking for " + facility.getName() + " has been approved.");
            auditTrail.record(actor, "booking.approved", "booking", id,
                    AuditTrail.details("status", BookingStatus.PENDING.name()),
                    AuditTrail.details("status", booking.getStatus().name(), "decision", d));
            return BookingResponse.from(booking);
        }
        if ("DECLINED".equals(d) || "REJECTED".equals(d)) {
            String cleanReason = clean(note);
            if (cleanReason == null) throw new InvalidBookingException("A rejection reason is required.");
            booking.reject(cleanReason, actor, clock.instant());
            bookings.saveAndFlush(booking);
            notifyRequester(booking, NotificationType.BOOKING_REJECTED,
                    "Booking rejected: " + booking.getReference(),
                    "Your booking was rejected. Reason: " + cleanReason);
            auditTrail.record(actor, "booking.rejected", "booking", id,
                    AuditTrail.details("status", BookingStatus.PENDING.name()),
                    AuditTrail.details("status", booking.getStatus().name(), "reason", cleanReason));
            return BookingResponse.from(booking);
        }
        throw new InvalidBookingException("decision must be APPROVED or DECLINED.");
    }

    @Transactional
    public BookingResponse returnAllocation(UUID id, Instant returnedAt, String conditionReturned, String notes, String actor) {
        Booking booking = lockBooking(id);
        String condition = clean(conditionReturned);
        if (condition == null) throw new InvalidBookingException("condition_returned is required.");
        String normalized = condition.trim().toUpperCase();
        if (!Set.of("WORKING","BEING_REPAIRED","DAMAGED","FAULTY","OK").contains(normalized)) {
            throw new InvalidBookingException("condition_returned must be WORKING or BEING_REPAIRED/DAMAGED.");
        }
        String stored = switch (normalized) {
            case "WORKING", "OK" -> "WORKING";
            case "DAMAGED" -> "DAMAGED";
            case "BEING_REPAIRED", "FAULTY" -> "BEING_REPAIRED";
            default -> throw new InvalidBookingException(
                    "condition_returned must be WORKING, BEING_REPAIRED, or DAMAGED.");
        };
        booking.markReturned(returnedAt, stored, notes, clock.instant());
        bookings.saveAndFlush(booking);
        auditTrail.record(actor, "booking.returned", "booking", id, null,
                AuditTrail.details("status", booking.getStatus().name(), "condition", stored));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse reschedule(UUID id, Instant startsAt, Instant endsAt, String actor) {
        validateTimes(startsAt, endsAt);
        Booking booking = lockBooking(id);
        Facility facility = lockFacility(booking);
        requireNotMaintenance(facility);
        requireMachineEligibilityIfNeeded(facility, booking.getRequesterId());
        requireNoConflict(facility.getId(), startsAt, endsAt, booking.getId());
        requireNoTimetableClash(facility.getId(), startsAt, endsAt);
        Instant previousStart = booking.getStartsAt();
        booking.reschedule(startsAt, endsAt, clock.instant());
        bookings.saveAndFlush(booking);
        notifyRequester(booking, NotificationType.BOOKING_RESCHEDULED,
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
        BookingStatus previousStatus = booking.getStatus();
        booking.cancel(clock.instant());
        bookings.saveAndFlush(booking);
        auditTrail.record(requesterId, "booking.cancelled", "booking", id,
                AuditTrail.details("status", previousStatus.name()),
                AuditTrail.details("status", BookingStatus.CANCELLED.name()));
        return BookingResponse.forSubmitter(booking);
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

    private void requireNoTimetableClash(UUID facilityId, Instant startsAt, Instant endsAt) {
        if (facilityId == null || timetable == null) return;
        if (!timetable.findOverlapping(facilityId, startsAt, endsAt).isEmpty()) {
            throw new BookingConflictException("The selected time clashes with a scheduled timetable block.");
        }
    }

    private void requireMachineEligibilityIfNeeded(Facility facility, String requesterId) {
        if (facility == null || requesterId == null || eligibilities == null
                || !facility.isSafetyPassRequired()) {
            return;
        }
        boolean ok = eligibilities.existsByUserIdAndDepartmentIgnoreCase(requesterId, "CNC");
        if (!ok) {
            throw new BookingConflictException("Safety training pass (CN5) required before booking this machine.");
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
