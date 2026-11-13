package org.unipod.unipoderpbackend.controller.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.booking.BookingResponse;
import org.unipod.unipoderpbackend.dto.booking.CreateBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.RejectBookingRequest;
import org.unipod.unipoderpbackend.dto.booking.RescheduleBookingRequest;
import org.unipod.unipoderpbackend.model.booking.BookingStatus;
import org.unipod.unipoderpbackend.service.booking.BookingService;

@RestController
@RequestMapping("/bookings")
@Validated
public class BookingController {
    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', "
            + "'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', 'SECTOR_OFFICER', "
            + "'FINANCE_OFFICER', 'SUBMITTER')")
    public BookingResponse create(
            @Valid @RequestBody CreateBookingRequest request,
            Authentication authentication
    ) {
        return bookings.create(request, authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', "
            + "'RECEPTIONIST', 'SECTOR_OFFICER', 'FINANCE_OFFICER', 'OBSERVER')")
    public PageResponse<BookingResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return PageResponse.from(bookings.all(status, date, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', "
            + "'RECEPTIONIST', 'SECTOR_OFFICER', 'FINANCE_OFFICER', 'OBSERVER')")
    public BookingResponse get(@PathVariable UUID id) {
        return bookings.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', 'SECTOR_OFFICER')")
    public BookingResponse approve(@PathVariable UUID id, Authentication authentication) {
        return bookings.approve(id, authentication.getName());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', 'SECTOR_OFFICER')")
    public BookingResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectBookingRequest request,
            Authentication authentication
    ) {
        return bookings.reject(id, request.reason(), authentication.getName());
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', 'SECTOR_OFFICER')")
    public BookingResponse reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleBookingRequest request,
            Authentication authentication
    ) {
        return bookings.reschedule(id, request.startsAt(), request.endsAt(), authentication.getName());
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', 'SECTOR_OFFICER')")
    public BookingResponse checkIn(@PathVariable UUID id, Authentication authentication) {
        return bookings.checkIn(id, authentication.getName());
    }

    @PostMapping("/{id}/check-out")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', 'SECTOR_OFFICER')")
    public BookingResponse checkOut(@PathVariable UUID id, Authentication authentication) {
        return bookings.checkOut(id, authentication.getName());
    }
}
