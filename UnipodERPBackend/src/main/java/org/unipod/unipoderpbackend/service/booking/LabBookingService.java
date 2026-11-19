package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.booking.BookingResponse;
import org.unipod.unipoderpbackend.dto.booking.CreateBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.CreateLabBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.LabBookingResponse;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.BookingKind;
import org.unipod.unipoderpbackend.model.booking.DepartmentBookingDetail;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.PaymentStatus;
import org.unipod.unipoderpbackend.repository.booking.DepartmentBookingDetailRepository;
import org.unipod.unipoderpbackend.repository.operations.DepartmentLookup;
import org.unipod.unipoderpbackend.repository.operations.StaffNameQuery;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;

/**
 * Books the Telecoms lab. The booking itself goes through {@link BookingService}, which locks the facility
 * row and refuses a clash with another booking or a timetable block (409), so two people booking the same
 * hour are served one after the other. The audit entry holds ids, times and counts only; the purpose and
 * the name of who it is for are free text and stay out of the log (D-021).
 */
@Service
public class LabBookingService {
    private final BookingService bookings;
    private final DepartmentBookingDetailRepository details;
    private final DepartmentAccess access;
    private final DepartmentLookup departments;
    private final StaffNameQuery staffNames;
    private final AuditService audit;
    private final Clock clock;

    public LabBookingService(
            BookingService bookings,
            DepartmentBookingDetailRepository details,
            DepartmentAccess access,
            DepartmentLookup departments,
            StaffNameQuery staffNames,
            AuditService audit,
            Clock clock) {
        this.bookings = bookings;
        this.details = details;
        this.access = access;
        this.departments = departments;
        this.staffNames = staffNames;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public LabBookingResponse create(CreateLabBookingRequest request, Actor actor) {
        access.requireManage(actor, DepartmentCode.TELECOMS);
        UUID facility = access.requireFacility(DepartmentCode.TELECOMS);
        Instant starts = request.date().atTime(request.start()).atZone(clock.getZone()).toInstant();
        Instant ends = request.date().atTime(request.end()).atZone(clock.getZone()).toInstant();

        BookingResponse created = bookings.createApproved(new CreateBookingRequest(
                facility, request.purpose().strip(), starts, ends, request.groupSize(),
                null, null, null, null, null), actor.email());

        String bookedBy = request.bookedBy() == null || request.bookedBy().isBlank()
                ? staffNames.displayName(actor.email()) : request.bookedBy().strip();
        DepartmentBookingDetail detail = details.saveAndFlush(new DepartmentBookingDetail(
                created.id(), DepartmentCode.TELECOMS, BookingKind.LAB, bookedBy, request.customerType(),
                null, null, clock.instant()));
        audit.log(new AuditEvent(actor, "lab_booking.created", "booking", created.id(), null,
                Map.of("facilityId", facility.toString(),
                        "status", created.status().name(),
                        "startsAt", starts.toString(),
                        "endsAt", ends.toString(),
                        "groupSize", request.groupSize()),
                departments.sectorId(DepartmentCode.TELECOMS).orElse(null)));

        Booking booking = details.findBooking(DepartmentCode.TELECOMS, created.id()).orElseThrow();
        return LabBookingResponse.from(booking, detail, PaymentStatus.NOT_REQUESTED, clock.getZone());
    }
}
