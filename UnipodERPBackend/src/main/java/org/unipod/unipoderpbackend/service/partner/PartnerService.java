package org.unipod.unipoderpbackend.service.partner;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.partner.PartnerResponse;
import org.unipod.unipoderpbackend.dto.partner.PartnersOverviewResponse;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;

@Service
public class PartnerService {
    private final PartnerRepository partners;
    private final PartnerStatusPolicy statusPolicy;

    public PartnerService(PartnerRepository partners, PartnerStatusPolicy statusPolicy) {
        this.partners = partners;
        this.statusPolicy = statusPolicy;
    }

    @Transactional(readOnly = true)
    public List<PartnerResponse> list() {
        return partners.findAllByRenewalOrder().stream()
                .map(partner -> PartnerResponse.from(partner, statusPolicy.statusOf(partner.getRenewalDate())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PartnersOverviewResponse overview() {
        List<PartnerResponse> all = list();
        long needingAttention = all.stream()
                .filter(partner -> partner.status() != PartnerStatus.ACTIVE)
                .count();
        return new PartnersOverviewResponse(all.size(), needingAttention, all);
    }
}
