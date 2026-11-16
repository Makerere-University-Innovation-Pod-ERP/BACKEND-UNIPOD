package org.unipod.unipoderpbackend.controller.booking;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.booking.BookingResponse;
import org.unipod.unipoderpbackend.dto.operations.AllocationDecisionRequest;
import org.unipod.unipoderpbackend.dto.operations.AllocationReturnRequest;
import org.unipod.unipoderpbackend.service.booking.BookingService;

@RestController
@RequestMapping("/allocations")
public class AllocationController {
    private final BookingService bookings;
    public AllocationController(BookingService bookings) { this.bookings = bookings; }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', 'SECTOR_OFFICER')")
    public BookingResponse decision(@PathVariable UUID id, @Valid @RequestBody AllocationDecisionRequest request, Authentication authentication) {
        return bookings.decide(id, request.decision(), request.note(), authentication.getName());
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', 'SECTOR_OFFICER')")
    public BookingResponse returned(@PathVariable UUID id, @Valid @RequestBody AllocationReturnRequest request, Authentication authentication) {
        return bookings.returnAllocation(id, request.returned_at(), request.condition_returned(), request.notes(), authentication.getName());
    }
}

