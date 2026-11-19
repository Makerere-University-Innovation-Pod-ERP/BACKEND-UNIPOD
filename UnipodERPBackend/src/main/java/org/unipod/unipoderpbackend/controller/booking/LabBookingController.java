package org.unipod.unipoderpbackend.controller.booking;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.controller.operations.OperationsRoles;
import org.unipod.unipoderpbackend.dto.booking.LabCalendarResponse;
import org.unipod.unipoderpbackend.service.booking.LabBookingQueryService;

/** The Telecoms lab screen (frontend {@code services/telecoms.js}, lab bookings). */
@RestController
@RequestMapping("/telecoms/lab-bookings")
@Validated
public class LabBookingController {
    static final String READ_ROLES = OperationsRoles.READ;

    private final LabBookingQueryService labBookings;

    public LabBookingController(LabBookingQueryService labBookings) {
        this.labBookings = labBookings;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public LabCalendarResponse calendar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return labBookings.calendar(from, to);
    }
}
