package org.unipod.unipoderpbackend.model.notification;

import java.util.UUID;

public record NotificationCreatedEvent(
        UUID id,
        String recipient,
        NotificationType type,
        String title,
        String body,
        String entityType,
        UUID entityId
) {
}
