package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.dto.booking.CreateBookingRequest;
import org.unipod.unipoderpbackend.exception.booking.BookingConflictException;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.Facility;
import org.unipod.unipoderpbackend.model.booking.FacilityStatus;
import org.unipod.unipoderpbackend.repository.booking.BookingRepository;
import org.unipod.unipoderpbackend.repository.booking.FacilityRepository;
import org.unipod.unipoderpbackend.repository.operations.MachineBookingEligibilityRepository;
import org.unipod.unipoderpbackend.repository.operations.TimetableBlockRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BookingServiceTest {
    private static final UUID FACILITY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-16T09:00:00Z");
    private static final String REQUESTER = "submitter@example.org";

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final FacilityRepository facilities = mock(FacilityRepository.class);
    private final MachineBookingEligibilityRepository eligibilities =
            mock(MachineBookingEligibilityRepository.class);
    private final TimetableBlockRepository timetable = mock(TimetableBlockRepository.class);
    private final BookingService service = new BookingService(
            bookings,
            facilities,
            mock(NotificationService.class),
            mock(AuditTrail.class),
            timetable,
            eligibilities,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void requiresPassForExplicitlyFlaggedFacilityWithoutDepartment() {
        Facility facility = facility("Laser cutter", null, true);
        when(facilities.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));

        assertThrows(BookingConflictException.class, () -> service.create(request(), REQUESTER));

        verify(eligibilities).existsByUserIdAndDepartmentIgnoreCase(REQUESTER, "CNC");
        verify(bookings, never()).saveAndFlush(any(Booking.class));
    }

    @Test
    void doesNotInferPassRequirementFromFacilityName() {
        Facility facility = facility("CNC conference room", null, false);
        when(facilities.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(bookings.nextReferenceNumber()).thenReturn(1L);
        when(timetable.findOverlapping(
                FACILITY_ID, NOW.plusSeconds(60), NOW.plusSeconds(3600)
        )).thenReturn(List.of());

        service.create(request(), REQUESTER);

        verifyNoInteractions(eligibilities);
    }

    private static Facility facility(String name, String department, boolean requiresSafetyPass) {
        return new Facility(
                FACILITY_ID,
                name,
                null,
                4,
                FacilityStatus.AVAILABLE,
                department,
                requiresSafetyPass,
                "admin@example.org",
                NOW
        );
    }

    private static CreateBookingRequest request() {
        return new CreateBookingRequest(
                FACILITY_ID,
                "Equipment work",
                NOW.plusSeconds(60),
                NOW.plusSeconds(3600),
                1,
                null,
                null,
                null,
                null,
                null
        );
    }
}
