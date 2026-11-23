package org.unipod.unipoderpbackend.controller.auth;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
import org.unipod.unipoderpbackend.dto.sector.AdminSectorResponse;
import org.unipod.unipoderpbackend.dto.sector.SectorRequest;
import org.unipod.unipoderpbackend.service.config.ConfigService;
import org.unipod.unipoderpbackend.service.sector.SectorService;

@RestController
@RequestMapping("/admin/departments")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminDepartmentController {
    private final SectorService sectors;
    private final ConfigService config;

    public AdminDepartmentController(SectorService sectors, ConfigService config) {
        this.sectors = sectors;
        this.config = config;
    }

    @GetMapping
    public List<AdminSectorResponse> list() {
        return sectors.allSectors();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminSectorResponse create(@Valid @RequestBody SectorRequest request) {
        AdminSectorResponse created = sectors.create(request);
        config.refresh();
        return created;
    }

    @PutMapping("/{id}")
    public AdminSectorResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody SectorRequest request
    ) {
        AdminSectorResponse updated = sectors.update(id, request);
        config.refresh();
        return updated;
    }
}
