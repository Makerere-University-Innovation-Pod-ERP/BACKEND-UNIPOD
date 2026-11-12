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
import org.unipod.unipoderpbackend.dto.partner.RecordMouRenewalRequest;
import org.unipod.unipoderpbackend.dto.partner.UpdatePartnerRequest;
import org.unipod.unipoderpbackend.exception.partner.PartnerConflictException;
import org.unipod.unipoderpbackend.exception.partner.PartnerNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.partner.Partner;
import org.unipod.unipoderpbackend.model.partner.PartnerStatus;
import org.unipod.unipoderpbackend.repository.partner.PartnerRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;

@Service
public class PartnerService {
    private static final String ENTITY_TYPE = "partner";
    private static final String CREATED = "partner.created";
    private static final String UPDATED = "partner.updated";
    private static final String MOU_RENEWED = "partner.mou_renewed";

    private final PartnerRepository partners;
    private final PartnerStatusPolicy statusPolicy;
    private final AuditService audit;
    private final Clock clock;

    public PartnerService(
            PartnerRepository partners, PartnerStatusPolicy statusPolicy, AuditService audit, Clock clock) {
        this.partners = partners;
        this.statusPolicy = statusPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PartnerResponse create(Actor actor, CreatePartnerRequest request) {
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
        audit.log(AuditEvent.created(actor, CREATED, ENTITY_TYPE, partner.getId(), PartnerAuditSnapshot.of(partner, false)));
        return toResponse(partner);
    }

    @Transactional
    public PartnerResponse update(Actor actor, UUID id, UpdatePartnerRequest request) {
        Partner partner = partners.findById(id).orElseThrow(PartnerNotFoundException::new);
        if (!request.version().equals(partner.getVersion())) {
            throw staleVersion();
        }
        // Checked before the entity changes, so the query cannot flush a half-applied rename.
        if (request.name() != null && !request.name().isBlank()
                && partners.existsByNameIgnoringCaseExcluding(request.name().strip(), id)) {
            throw nameTaken();
        }
        PartnerAuditSnapshot before = PartnerAuditSnapshot.of(partner, false);
        Contacts contactsBefore = Contacts.of(partner);
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
        auditChange(actor, UPDATED, partner, before, contactsBefore);
        return toResponse(partner);
    }

    @Transactional
    public PartnerResponse recordMouRenewal(Actor actor, UUID id, RecordMouRenewalRequest request) {
        Partner partner = partners.findById(id).orElseThrow(PartnerNotFoundException::new);
        PartnerAuditSnapshot before = PartnerAuditSnapshot.of(partner, false);
        Contacts contactsBefore = Contacts.of(partner);
        partner.renewMou(request.renewalDate(), request.documentReference(), clock.instant());
        try {
            partners.saveAndFlush(partner);
        } catch (ObjectOptimisticLockingFailureException concurrentEdit) {
            throw staleVersion();
        }
        auditChange(actor, MOU_RENEWED, partner, before, contactsBefore);
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

    // A request that leaves every audited value as it was is not worth an entry in an append-only log.
    private void auditChange(
            Actor actor, String action, Partner partner, PartnerAuditSnapshot before, Contacts contactsBefore) {
        PartnerAuditSnapshot after = PartnerAuditSnapshot.of(partner, !Contacts.of(partner).equals(contactsBefore));
        if (!after.equals(before)) {
            audit.log(AuditEvent.changed(actor, action, ENTITY_TYPE, partner.getId(), before, after));
        }
    }

    private PartnerResponse toResponse(Partner partner) {
        return PartnerResponse.from(partner, statusPolicy.statusOf(partner.getRenewalDate()));
    }

    // Compared only to learn whether contact details changed; never written to the audit log.
    private record Contacts(String person, String email) {
        static Contacts of(Partner partner) {
            return new Contacts(partner.getContactPerson(), partner.getContactEmail());
        }
    }

    private static PartnerConflictException staleVersion() {
        return new PartnerConflictException("This partner was changed by someone else. Reload it and try again.");
    }

    private static PartnerConflictException nameTaken() {
        return new PartnerConflictException("A partner with this name already exists.");
    }
}
