package org.unipod.unipoderpbackend.controller.training;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.training.BootcampResponse;
import org.unipod.unipoderpbackend.dto.training.EnrollBootcampRequest;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.training.BootcampEnrolService;

/** Enrolling someone in a bootcamp (frontend {@code enrollBootcamp}); answers with the whole session. */
@RestController
@RequestMapping("/telecoms/bootcamps")
@Validated
public class BootcampEnrolController {
    private final BootcampEnrolService enrolment;
    private final CurrentActor currentActor;

    public BootcampEnrolController(BootcampEnrolService enrolment, CurrentActor currentActor) {
        this.enrolment = enrolment;
        this.currentActor = currentActor;
    }

    @PostMapping("/{id}/enroll")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')")
    public BootcampResponse enroll(
            @PathVariable UUID id, @Valid @RequestBody EnrollBootcampRequest request, Authentication authentication) {
        return enrolment.enroll(id, request, currentActor.from(authentication));
    }
}
