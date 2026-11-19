package org.unipod.unipoderpbackend.controller.booking;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.booking.AddAttendeeRequest;
import org.unipod.unipoderpbackend.dto.booking.CancelTextileBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.SetAttendanceRequest;
import org.unipod.unipoderpbackend.dto.booking.TextileBookingResponse;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.booking.TextileBookingCommandService;

/** Cancelling a workshop booking and managing who signed up for a training session. */
@RestController
@RequestMapping("/textile/bookings")
@Validated
public class TextileBookingCommandController {
    private static final String MANAGE_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')";

    private final TextileBookingCommandService bookings;
    private final CurrentActor currentActor;

    public TextileBookingCommandController(TextileBookingCommandService bookings, CurrentActor currentActor) {
        this.bookings = bookings;
        this.currentActor = currentActor;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(MANAGE_ROLES)
    public TextileBookingResponse cancel(
            @PathVariable UUID id, @Valid @RequestBody CancelTextileBookingRequest request,
            Authentication authentication) {
        return bookings.cancel(id, request.reason(), currentActor.from(authentication));
    }

    @PostMapping("/{id}/attendees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_ROLES)
    public TextileBookingResponse addAttendee(
            @PathVariable UUID id, @Valid @RequestBody AddAttendeeRequest request, Authentication authentication) {
        return bookings.addAttendee(id, request.name(), currentActor.from(authentication));
    }

    @PutMapping("/{id}/attendees/{attendeeId}")
    @PreAuthorize(MANAGE_ROLES)
    public TextileBookingResponse setAttendance(
            @PathVariable UUID id, @PathVariable UUID attendeeId, @Valid @RequestBody SetAttendanceRequest request,
            Authentication authentication) {
        return bookings.setAttendance(id, attendeeId, request.attended(), currentActor.from(authentication));
    }
}
