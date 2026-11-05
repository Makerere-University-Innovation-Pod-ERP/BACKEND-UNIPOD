package org.unipod.unipoderpbackend.submissions;

import java.time.Instant;
import java.util.UUID;

public record SectorResponse(
        UUID id,
        String name,
        String notificationEmail,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static SectorResponse from(Sector sector) {
        return new SectorResponse(
                sector.getId(),
                sector.getName(),
                sector.getNotificationEmail(),
                sector.isActive(),
                sector.getVersion(),
                sector.getCreatedAt(),
                sector.getUpdatedAt()
        );
    }
}
