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
import org.unipod.unipoderpbackend.dto.training.SaveBootcampAttendanceRequest;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.training.BootcampAttendanceService;

/** Saving a bootcamp's attendance roster (frontend {@code saveAttendance}); answers with the whole session. */
@RestController
@RequestMapping("/telecoms/bootcamps")
@Validated
public class BootcampAttendanceController {
    private final BootcampAttendanceService attendance;
    private final CurrentActor currentActor;

    public BootcampAttendanceController(BootcampAttendanceService attendance, CurrentActor currentActor) {
        this.attendance = attendance;
        this.currentActor = currentActor;
    }

    @PostMapping("/{id}/attendance")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')")
    public BootcampResponse save(
            @PathVariable UUID id, @Valid @RequestBody SaveBootcampAttendanceRequest request,
            Authentication authentication) {
        return attendance.save(id, request, currentActor.from(authentication));
    }
}
