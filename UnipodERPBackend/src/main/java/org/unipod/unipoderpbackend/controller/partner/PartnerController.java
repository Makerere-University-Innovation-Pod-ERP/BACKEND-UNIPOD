package org.unipod.unipoderpbackend.controller.partner;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.partner.CreatePartnerRequest;
import org.unipod.unipoderpbackend.dto.partner.PartnerResponse;
import org.unipod.unipoderpbackend.dto.partner.PartnersOverviewResponse;
import org.unipod.unipoderpbackend.dto.partner.RecordMouRenewalRequest;
import org.unipod.unipoderpbackend.dto.partner.UpdatePartnerRequest;
import org.unipod.unipoderpbackend.security.CurrentActor;
import org.unipod.unipoderpbackend.service.partner.PartnerService;

@RestController
@RequestMapping("/operations/partners")
@Validated
public class PartnerController {
    // Every role except SUBMITTER may read operations data (D-011).
    private static final String READ_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', "
            + "'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', "
            + "'SECTOR_OFFICER', 'IP_LIAISON', 'FINANCE_OFFICER', 'OBSERVER')";
    private static final String MANAGE_ROLES = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')";

    private final PartnerService partners;
    private final CurrentActor currentActor;

    public PartnerController(PartnerService partners, CurrentActor currentActor) {
        this.partners = partners;
        this.currentActor = currentActor;
    }

    @GetMapping("/overview")
    @PreAuthorize(READ_ROLES)
    public PartnersOverviewResponse overview() {
        return partners.overview();
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<PartnerResponse> list() {
        return partners.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE_ROLES)
    public PartnerResponse create(
            @Valid @RequestBody CreatePartnerRequest request, Authentication authentication) {
        return partners.create(currentActor.from(authentication), request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(MANAGE_ROLES)
    public PartnerResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdatePartnerRequest request, Authentication authentication) {
        return partners.update(currentActor.from(authentication), id, request);
    }

    @PutMapping("/{id}/mou")
    @PreAuthorize(MANAGE_ROLES)
    public PartnerResponse recordMouRenewal(
            @PathVariable UUID id,
            @Valid @RequestBody RecordMouRenewalRequest request,
            Authentication authentication) {
        return partners.recordMouRenewal(currentActor.from(authentication), id, request);
    }
}
