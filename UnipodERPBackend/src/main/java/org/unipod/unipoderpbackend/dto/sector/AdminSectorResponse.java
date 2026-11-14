package org.unipod.unipoderpbackend.dto.sector;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.sector.Sector;

/**
 * The administrator's sector catalog entry. Unlike the staff-wide directory it
 * includes the WhatsApp group invite link so the edit form can prefill and
 * preserve the stored value.
 */
public record AdminSectorResponse(
        UUID id,
        String name,
        String notificationEmail,
        String whatsappGroupUrl,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminSectorResponse from(Sector sector) {
        return new AdminSectorResponse(
                sector.getId(),
                sector.getName(),
                sector.getNotificationEmail(),
                sector.getWhatsappGroupUrl(),
                sector.isActive(),
                sector.getVersion(),
                sector.getCreatedAt(),
                sector.getUpdatedAt()
        );
    }
}
