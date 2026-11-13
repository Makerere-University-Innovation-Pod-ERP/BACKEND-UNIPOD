package org.unipod.unipoderpbackend.controller.booking;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.booking.CreateFacilityRequest;
import org.unipod.unipoderpbackend.dto.booking.FacilityResponse;
import org.unipod.unipoderpbackend.dto.booking.UpdateFacilityStatusRequest;
import org.unipod.unipoderpbackend.service.booking.FacilityService;

@RestController
@RequestMapping("/facilities")
@Validated
public class FacilityController {
    private final FacilityService facilities;

    public FacilityController(FacilityService facilities) {
        this.facilities = facilities;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', "
            + "'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', 'SECTOR_OFFICER', "
            + "'FINANCE_OFFICER', 'SUBMITTER', 'OBSERVER')")
    public List<FacilityResponse> list() {
        return facilities.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', 'SECTOR_OFFICER')")
    public FacilityResponse create(
            @Valid @RequestBody CreateFacilityRequest request,
            Authentication authentication
    ) {
        return facilities.create(request, authentication.getName());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'DEPARTMENT_COORDINATOR', 'SECTOR_OFFICER')")
    public FacilityResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFacilityStatusRequest request,
            Authentication authentication
    ) {
        return facilities.updateStatus(id, request.status(), authentication.getName());
    }
}
