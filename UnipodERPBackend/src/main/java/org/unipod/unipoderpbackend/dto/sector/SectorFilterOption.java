package org.unipod.unipoderpbackend.dto.sector;

import java.util.UUID;
import org.unipod.unipoderpbackend.model.sector.Sector;

public record SectorFilterOption(UUID id, String name) {
    public static SectorFilterOption from(Sector sector) {
        return new SectorFilterOption(sector.getId(), sector.getName());
    }
}
