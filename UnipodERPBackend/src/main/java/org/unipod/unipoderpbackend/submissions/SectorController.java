package org.unipod.unipoderpbackend.submissions;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/sectors")
public class SectorController {
    private final SectorService sectors;

    public SectorController(SectorService sectors) {
        this.sectors = sectors;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public List<SectorResponse> activeSectors() {
        return sectors.activeSectors();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public SectorResponse create(@Valid @RequestBody SectorRequest request) {
        return sectors.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public SectorResponse update(@PathVariable UUID id, @Valid @RequestBody SectorRequest request) {
        return sectors.update(id, request);
    }
}
