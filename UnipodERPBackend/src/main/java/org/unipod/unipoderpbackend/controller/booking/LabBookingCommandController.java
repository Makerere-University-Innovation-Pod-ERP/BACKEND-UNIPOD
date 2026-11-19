package org.unipod.unipoderpbackend.controller.booking;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.booking.CreateLabBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.LabBookingResponse;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.booking.LabBookingService;

/** Booking the lab (frontend {@code createLabBooking}). Reading the calendar is {@link LabBookingController}. */
@RestController
@RequestMapping("/telecoms/lab-bookings")
@Validated
public class LabBookingCommandController {
    private static final String MANAGE_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')";

    private final LabBookingService labBookings;
    private final CurrentActor currentActor;

    public LabBookingCommandController(LabBookingService labBookings, CurrentActor currentActor) {
        this.labBookings = labBookings;
        this.currentActor = currentActor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_ROLES)
    public LabBookingResponse create(
            @Valid @RequestBody CreateLabBookingRequest request, Authentication authentication) {
        return labBookings.create(request, currentActor.from(authentication));
    }
}
