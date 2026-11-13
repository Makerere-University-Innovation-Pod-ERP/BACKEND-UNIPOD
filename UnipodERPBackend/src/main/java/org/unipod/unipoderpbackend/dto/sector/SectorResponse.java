package org.unipod.unipoderpbackend.dto.sector;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.sector.Sector;

public record SectorResponse(
        UUID id,
        String name,
        String notificationEmail,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String whatsappGroupUrl
) {
    public static SectorResponse from(Sector sector) {
        return new SectorResponse(
                sector.getId(),
                sector.getName(),
                sector.getNotificationEmail(),
                sector.isActive(),
                sector.getVersion(),
                sector.getCreatedAt(),
                sector.getUpdatedAt(),
                sector.getWhatsappGroupUrl()
        );
    }
}
