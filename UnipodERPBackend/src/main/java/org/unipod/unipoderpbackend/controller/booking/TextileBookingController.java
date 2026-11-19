package org.unipod.unipoderpbackend.controller.booking;

import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.booking.CreateTextileBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.TextileBookingResponse;
import org.unipod.unipoderpbackend.dto.booking.TextileCalendarResponse;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.booking.TextileBookingService;

/** The textile workshop screen (frontend {@code services/textile.js}, bookings). */
@RestController
@RequestMapping("/textile/bookings")
@Validated
public class TextileBookingController {
    private static final String MANAGE_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')";

    private final TextileBookingService bookings;
    private final CurrentActor currentActor;

    public TextileBookingController(TextileBookingService bookings, CurrentActor currentActor) {
        this.bookings = bookings;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize(LabBookingController.READ_ROLES)
    public TextileCalendarResponse calendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookings.calendar(from, to);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_ROLES)
    public TextileBookingResponse create(
            @Valid @RequestBody CreateTextileBookingRequest request, Authentication authentication) {
        return bookings.create(request, currentActor.from(authentication));
    }
}
