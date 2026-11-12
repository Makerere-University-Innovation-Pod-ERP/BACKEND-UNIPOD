package org.unipod.unipoderpbackend.controller.submission;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckResponse;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckUpdateRequest;
import org.unipod.unipoderpbackend.service.submission.ReceptionChecklistService;

@RestController
@RequestMapping("/submissions/{id}/reception-checklist")
@Validated
public class ReceptionChecklistController {
    private final ReceptionChecklistService checklists;

    public ReceptionChecklistController(ReceptionChecklistService checklists) {
        this.checklists = checklists;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public ReceptionCheckResponse get(@PathVariable UUID id, Authentication authentication) {
        return checklists.get(id, authentication);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public ReceptionCheckResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody ReceptionCheckUpdateRequest request,
            Authentication authentication
    ) {
        return checklists.update(id, request, authentication);
    }
}
