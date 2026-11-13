package org.unipod.unipoderpbackend.service.sector;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.sector.SectorRequest;
import org.unipod.unipoderpbackend.dto.sector.SectorResponse;
import org.unipod.unipoderpbackend.dto.sector.SectorFilterOption;
import org.unipod.unipoderpbackend.exception.sector.SectorConflictException;
import org.unipod.unipoderpbackend.exception.sector.SectorNotFoundException;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.repository.sector.SectorRepository;

@Service
public class SectorService {
    private final SectorRepository sectors;

    public SectorService(SectorRepository sectors) {
        this.sectors = sectors;
    }

    @Transactional(readOnly = true)
    public List<SectorResponse> activeSectors() {
        return sectors.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(SectorResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SectorResponse> allSectors() {
        return sectors.findAllByOrderByNameAsc()
                .stream()
                .map(SectorResponse::from)
                .toList();
    }

    /**
     * Looks up the active department an intake form named, so the submitter
     * confirmation can share that department's invite link without exposing it
     * on the general sector directory.
     */
    @Transactional(readOnly = true)
    public Optional<Sector> activeSectorByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return sectors.findFirstByNameIgnoreCaseAndActiveTrue(name.trim());
    }

    @Transactional(readOnly = true)
    public List<SectorFilterOption> filterOptions() {
        return sectors.findAllByOrderByNameAsc()
                .stream()
                .map(SectorFilterOption::from)
                .toList();
    }

    @Transactional
    public SectorResponse create(SectorRequest request) {
        String name = request.name().trim();
        if (sectors.existsByNameIgnoreCase(name)) {
            throw new SectorConflictException();
        }
        Instant now = Instant.now();
        try {
            Sector sector = sectors.saveAndFlush(new Sector(
                    UUID.randomUUID(),
                    name,
                    request.notificationEmail().trim(),
                    normalizeWhatsappGroupUrl(request.whatsappGroupUrl()),
                    request.active(),
                    now,
                    now
            ));
            return SectorResponse.from(sector);
        } catch (DataIntegrityViolationException exception) {
            throw conflictIfNameConstraint(exception);
        }
    }

    @Transactional
    public SectorResponse update(UUID id, SectorRequest request) {
        Sector sector = sectors.findById(id).orElseThrow(() -> new SectorNotFoundException(id));
        if (request.version() == null || request.version() != sector.getVersion()) {
            throw new SectorConflictException();
        }
        String name = request.name().trim();
        if (sectors.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new SectorConflictException();
        }
        // An omitted link keeps the stored one; send an explicit empty string to
        // remove it. This avoids clearing the invite link on unrelated edits.
        String whatsappGroupUrl = request.whatsappGroupUrl() == null
                ? sector.getWhatsappGroupUrl()
                : normalizeWhatsappGroupUrl(request.whatsappGroupUrl());
        sector.update(
                name,
                request.notificationEmail().trim(),
                whatsappGroupUrl,
                request.active(),
                Instant.now()
        );
        try {
            sectors.flush();
            return SectorResponse.from(sector);
        } catch (DataIntegrityViolationException exception) {
            throw conflictIfNameConstraint(exception);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new SectorConflictException();
        }
    }

    @Transactional
    public Sector requireActive(UUID id) {
        Sector sector = sectors.findByIdForShare(id).orElseThrow(() -> new SectorNotFoundException(id));
        if (!sector.isActive()) {
            throw new SectorNotFoundException(id);
        }
        return sector;
    }

    private static String normalizeWhatsappGroupUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static SectorConflictException conflictIfNameConstraint(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("uq_sectors_name_ci")) {
                return new SectorConflictException();
            }
            cause = cause.getCause();
        }
        throw exception;
    }
}
