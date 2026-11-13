package org.unipod.unipoderpbackend.service.booking;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.booking.CreateFacilityRequest;
import org.unipod.unipoderpbackend.dto.booking.FacilityResponse;
import org.unipod.unipoderpbackend.exception.booking.BookingConflictException;
import org.unipod.unipoderpbackend.exception.booking.FacilityNotFoundException;
import org.unipod.unipoderpbackend.model.booking.Facility;
import org.unipod.unipoderpbackend.model.booking.FacilityStatus;
import org.unipod.unipoderpbackend.repository.booking.FacilityRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;

@Service
public class FacilityService {
    private final FacilityRepository facilities;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public FacilityService(FacilityRepository facilities, AuditTrail auditTrail, Clock clock) {
        this.facilities = facilities;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FacilityResponse> list() {
        return facilities.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(FacilityResponse::from)
                .toList();
    }

    @Transactional
    public FacilityResponse create(CreateFacilityRequest request, String actor) {
        String name = request.name().trim();
        if (facilities.existsByNameIgnoreCase(name)) {
            throw new BookingConflictException("A facility with this name already exists.");
        }
        Instant now = clock.instant();
        Facility facility = new Facility(
                UUID.randomUUID(),
                name,
                clean(request.location()),
                request.capacity(),
                request.status(),
                clean(request.department()),
                actor,
                now
        );
        try {
            facilities.saveAndFlush(facility);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateName(exception)) {
                throw new BookingConflictException("A facility with this name already exists.");
            }
            throw exception;
        }
        auditTrail.record(actor, "facility.created", "facility", facility.getId(), null,
                AuditTrail.details("name", facility.getName(), "status", facility.getStatus().name()));
        return FacilityResponse.from(facility);
    }

    private static boolean isDuplicateName(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            if (message.contains("uq_facilities_name_lower")
                    || message.contains("uq_facilities_name")
                    || message.contains("duplicate key value")) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public FacilityResponse updateStatus(UUID id, FacilityStatus status, String actor) {
        Facility facility = facilities.findByIdForUpdate(id)
                .orElseThrow(() -> new FacilityNotFoundException(id));
        FacilityStatus previous = facility.getStatus();
        facility.updateStatus(status, clock.instant());
        facilities.saveAndFlush(facility);
        auditTrail.record(actor, "facility.status_changed", "facility", id,
                AuditTrail.details("status", previous.name()),
                AuditTrail.details("status", status.name()));
        return FacilityResponse.from(facility);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
