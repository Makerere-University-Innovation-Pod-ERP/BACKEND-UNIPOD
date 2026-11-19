package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unipod.unipoderpbackend.dto.booking.BookingResponse;
import org.unipod.unipoderpbackend.dto.booking.CreateBookingRequest;
import org.unipod.unipoderpbackend.exception.booking.BookingConflictException;
import org.unipod.unipoderpbackend.model.booking.Booking;
import org.unipod.unipoderpbackend.model.booking.Facility;
import org.unipod.unipoderpbackend.model.booking.FacilityStatus;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.repository.booking.BookingRepository;
import org.unipod.unipoderpbackend.repository.booking.FacilityRepository;
import org.unipod.unipoderpbackend.repository.operations.MachineBookingEligibilityRepository;
import org.unipod.unipoderpbackend.repository.operations.TimetableBlockRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.notification.NotificationService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private final NotificationService notificationService = mock(NotificationService.class);
    private final BookingService service = new BookingService(
            bookings,
            facilities,
            notificationService,
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

    @Test
    void notifiesTheSubmitterWhenTheirBookingRequestIsCreated() {
        Facility facility = facility("Workshop", null, false);
        when(facilities.findByIdForUpdate(FACILITY_ID)).thenReturn(Optional.of(facility));
        when(bookings.nextReferenceNumber()).thenReturn(1L);
        when(timetable.findOverlapping(
                FACILITY_ID, NOW.plusSeconds(60), NOW.plusSeconds(3600)
        )).thenReturn(List.of());

        BookingResponse created = service.create(request(), REQUESTER);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationService).publish(eventCaptor.capture());
        NotificationEvent event = eventCaptor.getValue();
        assertEquals(NotificationType.BOOKING_REQUESTED, event.type());
        assertEquals(Set.of(REQUESTER), event.recipients());
        assertEquals("booking", event.entityType());
        assertEquals(created.id(), event.entityId());
        assertEquals("Booking request received: " + created.reference(), event.title());
    }

    @Test
    void notifiesTheSubmitterWhenTheirBookingIsCancelled() {
        UUID bookingId = UUID.randomUUID();
        Facility facility = facility("Workshop", null, false);
        Booking booking = new Booking(
                bookingId,
                "BKG-2026-000001",
                facility,
                REQUESTER,
                "Equipment work",
                NOW.plusSeconds(60),
                NOW.plusSeconds(3600),
                1,
                null,
                NOW
        );
        when(bookings.findByIdAndRequesterId(bookingId, REQUESTER)).thenReturn(Optional.of(booking));
        when(bookings.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookings.saveAndFlush(booking)).thenReturn(booking);

        service.cancel(bookingId, REQUESTER);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationService).publish(eventCaptor.capture());
        NotificationEvent event = eventCaptor.getValue();
        assertEquals(NotificationType.BOOKING_CANCELLED, event.type());
        assertEquals(Set.of(REQUESTER), event.recipients());
        assertEquals("booking", event.entityType());
        assertEquals(bookingId, event.entityId());
        assertEquals("Booking cancelled: BKG-2026-000001", event.title());
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
