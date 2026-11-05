package org.unipod.unipoderpbackend.submissions;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    @Transactional
    public SectorResponse create(SectorRequest request) {
        String name = request.name().trim();
        if (sectors.existsByNameIgnoreCase(name)) {
            throw new SectorConflictException();
        }
        Instant now = Instant.now();
        Sector sector = sectors.save(new Sector(
                UUID.randomUUID(),
                name,
                request.notificationEmail().trim(),
                request.active(),
                now,
                now
        ));
        return SectorResponse.from(sector);
    }

    @Transactional
    public SectorResponse update(UUID id, SectorRequest request) {
        Sector sector = sectors.findById(id).orElseThrow(() -> new SectorNotFoundException(id));
        String name = request.name().trim();
        if (sectors.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new SectorConflictException();
        }
        sector.update(name, request.notificationEmail().trim(), request.active(), Instant.now());
        return SectorResponse.from(sector);
    }

    @Transactional(readOnly = true)
    public Sector requireActive(UUID id) {
        Sector sector = sectors.findByIdForShare(id).orElseThrow(() -> new SectorNotFoundException(id));
        if (!sector.isActive()) {
            throw new SectorNotFoundException(id);
        }
        return sector;
    }
}
