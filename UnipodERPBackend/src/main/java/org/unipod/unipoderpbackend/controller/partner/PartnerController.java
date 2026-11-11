package org.unipod.unipoderpbackend.controller.partner;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
