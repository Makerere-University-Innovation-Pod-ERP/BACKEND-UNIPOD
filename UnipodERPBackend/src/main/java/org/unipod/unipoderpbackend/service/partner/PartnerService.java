package org.unipod.unipoderpbackend.service.partner;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.partner.CreatePartnerRequest;
import org.unipod.unipoderpbackend.dto.partner.PartnerResponse;
import org.unipod.unipoderpbackend.dto.partner.PartnersOverviewResponse;
import org.unipod.unipoderpbackend.dto.partner.UpdatePartnerRequest;
import org.unipod.unipoderpbackend.exception.partner.PartnerConflictException;
import org.unipod.unipoderpbackend.exception.partner.PartnerNotFoundException;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;

@Service
public class PartnerService {
    private final PartnerRepository partners;
    private final PartnerStatusPolicy statusPolicy;
    private final Clock clock;

    public PartnerService(PartnerRepository partners, PartnerStatusPolicy statusPolicy, Clock clock) {
        this.partners = partners;
        this.statusPolicy = statusPolicy;
        this.clock = clock;
    }

    @Transactional
    public PartnerResponse create(CreatePartnerRequest request) {
        Partner partner = new Partner(
                UUID.randomUUID(),
                request.name(),
                request.type(),
                request.renewalDate(),
                request.contactPerson(),
                request.contactEmail(),
                request.notes(),
                clock.instant());
        if (partners.existsByNameIgnoringCase(partner.getName())) {
            throw nameTaken();
        }
        try {
            partners.saveAndFlush(partner);
        } catch (DataIntegrityViolationException race) {
            // Two requests can pass the check above together; uq_partners_name is the only
            // constraint the entity does not already guard, so it decides.
            throw nameTaken();
        }
        return toResponse(partner);
    }

    @Transactional
    public PartnerResponse update(UUID id, UpdatePartnerRequest request) {
        Partner partner = partners.findById(id).orElseThrow(PartnerNotFoundException::new);
        if (!request.version().equals(partner.getVersion())) {
            throw staleVersion();
        }
        // Checked before the entity changes, so the query cannot flush a half-applied rename.
        if (request.name() != null && !request.name().isBlank()
                && partners.existsByNameIgnoringCaseExcluding(request.name().strip(), id)) {
            throw nameTaken();
        }
        partner.updateDetails(
                request.name(),
                request.type(),
                request.renewalDate(),
                request.contactPerson(),
                request.contactEmail(),
                request.notes(),
                clock.instant());
        try {
            partners.saveAndFlush(partner);
        } catch (DataIntegrityViolationException race) {
            throw nameTaken();
        } catch (ObjectOptimisticLockingFailureException concurrentEdit) {
            throw staleVersion();
        }
        return toResponse(partner);
    }

    @Transactional(readOnly = true)
    public List<PartnerResponse> list() {
        return partners.findAllByRenewalOrder().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PartnersOverviewResponse overview() {
        List<PartnerResponse> all = list();
        long needingAttention = all.stream()
                .filter(partner -> partner.status() != PartnerStatus.ACTIVE)
                .count();
        return new PartnersOverviewResponse(all.size(), needingAttention, all);
    }

    private PartnerResponse toResponse(Partner partner) {
        return PartnerResponse.from(partner, statusPolicy.statusOf(partner.getRenewalDate()));
    }

    private static PartnerConflictException staleVersion() {
        return new PartnerConflictException("This partner was changed by someone else. Reload it and try again.");
    }

    private static PartnerConflictException nameTaken() {
        return new PartnerConflictException("A partner with this name already exists.");
    }
}
