package org.unipod.unipoderpbackend.controller.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
import org.unipod.unipoderpbackend.service.booking.BookingService;

@RestController
@RequestMapping("/me/bookings")
@Validated
public class MyBookingController {
    private final BookingService bookings;

    public MyBookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUBMITTER')")
    public PageResponse<BookingResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return PageResponse.from(bookings.forRequester(
                authentication.getName(), PageRequest.of(page, size)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUBMITTER')")
    public BookingResponse get(@PathVariable UUID id, Authentication authentication) {
        return bookings.forRequester(id, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUBMITTER')")
    public BookingResponse create(
            @Valid @RequestBody CreateBookingRequest request,
            Authentication authentication
    ) {
        return bookings.create(request, authentication.getName());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SUBMITTER')")
    public BookingResponse cancel(@PathVariable UUID id, Authentication authentication) {
        return bookings.cancel(id, authentication.getName());
    }
}
