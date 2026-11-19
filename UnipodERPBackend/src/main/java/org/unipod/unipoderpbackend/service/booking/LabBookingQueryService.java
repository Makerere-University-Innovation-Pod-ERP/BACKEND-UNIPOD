package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.booking.LabBookingResponse;
import org.unipod.unipoderpbackend.dto.booking.LabBookingStatus;
import org.unipod.unipoderpbackend.dto.booking.LabCalendarResponse;
import org.unipod.unipoderpbackend.dto.booking.TimetableEntryResponse;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.DepartmentBookingDetail;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.PaymentStatus;
import org.unipod.unipoderpbackend.repository.booking.DepartmentBookingDetailRepository;
import org.unipod.unipoderpbackend.repository.operations.DepartmentLookup;
import org.unipod.unipoderpbackend.repository.operations.PaymentLinkQuery;

/** The Telecoms lab calendar: confirmed lab bookings and the weekly classes of the lab (D-041 to D-043). */
@Service
public class LabBookingQueryService {
    static final String INVOICE_SOURCE = "BOOKING";

    private final DepartmentBookingDetailRepository details;
    private final DepartmentLookup departments;
    private final PaymentLinkQuery payments;
    private final DepartmentTimetable timetable;
    private final Clock clock;

    public LabBookingQueryService(
            DepartmentBookingDetailRepository details,
            DepartmentLookup departments,
            PaymentLinkQuery payments,
            DepartmentTimetable timetable,
            Clock clock) {
        this.details = details;
        this.departments = departments;
        this.payments = payments;
        this.timetable = timetable;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LabCalendarResponse calendar(LocalDate from, LocalDate to) {
        CalendarWindow window = CalendarWindow.of(from, to, LocalDate.now(clock));
        List<Booking> bookings = details.findInWindow(
                DepartmentCode.TELECOMS, window.startInstant(clock.getZone()), window.endInstant(clock.getZone()),
                LabBookingStatus.CONFIRMED_STORED);
        Optional<UUID> facility = departments.facilityId(DepartmentCode.TELECOMS);
        List<TimetableEntryResponse> classes = facility.map(id -> timetable.weekly(id, window)).orElse(List.of());
        return new LabCalendarResponse(views(bookings), classes);
    }

    /** One query for the detail rows and one for the payments, however many bookings there are. */
    List<LabBookingResponse> views(List<Booking> bookings) {
        List<UUID> ids = bookings.stream().map(Booking::getId).toList();
        Map<UUID, DepartmentBookingDetail> detailById = details.findAllById(ids).stream()
                .collect(Collectors.toMap(DepartmentBookingDetail::getBookingId, Function.identity()));
        Map<String, PaymentStatus> paymentBySource = payments.bySource(
                INVOICE_SOURCE, ids.stream().map(UUID::toString).toList());
        return bookings.stream()
                .map(booking -> LabBookingResponse.from(
                        booking,
                        detailById.get(booking.getId()),
                        paymentBySource.getOrDefault(booking.getId().toString(), PaymentStatus.NOT_REQUESTED),
                        clock.getZone()))
                .toList();
    }
}
