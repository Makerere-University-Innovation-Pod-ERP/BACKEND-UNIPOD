package org.unipod.unipoderpbackend.controller.auth;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.sector.SectorFilterOption;
import org.unipod.unipoderpbackend.dto.sector.SectorRequest;
import org.unipod.unipoderpbackend.dto.sector.SectorResponse;
import org.unipod.unipoderpbackend.service.config.ConfigService;
import org.unipod.unipoderpbackend.service.sector.SectorService;

@RestController
@RequestMapping("/departments")
public class DepartmentController {
    private final SectorService sectors;
    private final ConfigService config;

    public DepartmentController(SectorService sectors, ConfigService config) {
        this.sectors = sectors;
        this.config = config;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SectorFilterOption> list() {
        return config.departments();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public SectorResponse create(@Valid @RequestBody SectorRequest request) {
        SectorResponse created = sectors.create(request);
        config.refresh();
        return created;
    }
}
