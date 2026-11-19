package org.unipod.unipoderpbackend.controller.operations;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.unipod.unipoderpbackend.dto.operations.MediaContractRequest;
import org.unipod.unipoderpbackend.dto.operations.MediaContractStatusRequest;
import org.unipod.unipoderpbackend.service.operations.MediaContractService;

@RestController
@RequestMapping("/media/contracts")
public class MediaContractController {
    private final MediaContractService contracts;
    public MediaContractController(MediaContractService contracts) { this.contracts = contracts; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','SECTOR_OFFICER','FINANCE_OFFICER')")
    public List<Map<String,Object>> list() { return contracts.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','SECTOR_OFFICER','FINANCE_OFFICER')")
    public Map<String,Object> create(@Valid @RequestBody MediaContractRequest request, Authentication auth) {
        if (Boolean.TRUE.equals(request.generate_invoices())
                && auth.getAuthorities().stream().noneMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMINISTRATOR")
                                || authority.getAuthority().equals("ROLE_FINANCE_OFFICER"))) {
            throw new AccessDeniedException("Only finance roles can generate invoices from a media contract.");
        }
        return contracts.create(request, auth.getName());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR','INNOVATION_COORDINATOR','DEPARTMENT_COORDINATOR','SECTOR_OFFICER')")
    public Map<String,Object> transition(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody MediaContractStatusRequest request,
            Authentication auth) {
        return contracts.transition(id, request, auth.getName());
    }
}

