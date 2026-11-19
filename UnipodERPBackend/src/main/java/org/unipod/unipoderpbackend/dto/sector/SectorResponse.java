package org.unipod.unipoderpbackend.dto.sector;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.sector.Sector;

/**
 * The department directory. The WhatsApp group invite link is deliberately
 * omitted: it is only shared with a submitter on the confirmation screen for
 * the department that received their brief.
 */
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
