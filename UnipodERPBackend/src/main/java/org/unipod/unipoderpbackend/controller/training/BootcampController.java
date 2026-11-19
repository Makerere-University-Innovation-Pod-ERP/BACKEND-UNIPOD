package org.unipod.unipoderpbackend.controller.training;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.training.BootcampListResponse;
import org.unipod.unipoderpbackend.dto.training.BootcampResponse;
import org.unipod.unipoderpbackend.dto.training.CreateBootcampRequest;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.training.BootcampService;

/** The Telecoms bootcamp screen (frontend {@code services/telecoms.js}, bootcamps). */
@RestController
@RequestMapping("/telecoms/bootcamps")
@Validated
public class BootcampController {
    private static final String READ_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', "
            + "'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', "
            + "'SECTOR_OFFICER', 'IP_LIAISON', 'FINANCE_OFFICER', 'OBSERVER')";
    private static final String MANAGE_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')";

    private final BootcampService bootcamps;
    private final CurrentActor currentActor;

    public BootcampController(BootcampService bootcamps, CurrentActor currentActor) {
        this.bootcamps = bootcamps;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public BootcampListResponse list() {
        return bootcamps.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_ROLES)
    public BootcampResponse create(@Valid @RequestBody CreateBootcampRequest request, Authentication authentication) {
        return bootcamps.create(request, currentActor.from(authentication));
    }
}
