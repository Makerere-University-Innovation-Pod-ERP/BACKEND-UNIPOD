package org.unipod.unipoderpbackend.controller.sector;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.sector.AdminSectorResponse;
import org.unipod.unipoderpbackend.dto.sector.SectorMemberRequest;
import org.unipod.unipoderpbackend.dto.sector.SectorRequest;
import org.unipod.unipoderpbackend.dto.sector.SectorResponse;
import org.unipod.unipoderpbackend.dto.sector.SectorFilterOption;
import org.unipod.unipoderpbackend.service.sector.SectorMemberService;
import org.unipod.unipoderpbackend.service.sector.SectorService;

@RestController
@RequestMapping("/sectors")
public class SectorController {
    private final SectorService sectors;
    private final SectorMemberService members;

    public SectorController(SectorService sectors, SectorMemberService members) {
        this.sectors = sectors;
        this.members = members;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public List<SectorResponse> activeSectors() {
        return sectors.activeSectors();
    }

    @GetMapping("/filter-options")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', "
            + "'FACT_FINDING_OFFICER', 'IP_LIAISON', 'OBSERVER', 'SUBMITTER', 'REVIEWER', 'SECTOR_OFFICER')")
    public List<SectorFilterOption> filterOptions() {
        return sectors.filterOptions();
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public List<AdminSectorResponse> allSectors() {
        return sectors.allSectors();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public AdminSectorResponse create(@Valid @RequestBody SectorRequest request) {
        return sectors.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public AdminSectorResponse update(@PathVariable UUID id, @Valid @RequestBody SectorRequest request) {
        return sectors.update(id, request);
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public List<String> members(@PathVariable UUID id) {
        return members.list(id);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public String addMember(@PathVariable UUID id, @Valid @RequestBody SectorMemberRequest request) {
        return members.add(id, request);
    }

    @DeleteMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public void removeMember(@PathVariable UUID id, @RequestParam String subject) {
        members.remove(id, subject);
    }
}
