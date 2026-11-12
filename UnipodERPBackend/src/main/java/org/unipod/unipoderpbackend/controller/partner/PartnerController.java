package org.unipod.unipoderpbackend.controller.partner;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.partner.CreatePartnerRequest;
import org.unipod.unipoderpbackend.dto.partner.PartnerResponse;
import org.unipod.unipoderpbackend.dto.partner.PartnersOverviewResponse;
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

    public PartnerController(PartnerService partners) {
        this.partners = partners;
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
    public PartnerResponse create(@Valid @RequestBody CreatePartnerRequest request) {
        return partners.create(request);
    }
}
