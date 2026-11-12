package org.unipod.unipoderpbackend.dto.notification;

import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.notification.Notification;
import org.unipod.unipoderpbackend.model.notification.NotificationType;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String entityType,
        UUID entityId,
        Instant createdAt,
        Instant readAt,
        boolean read
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.isRead()
        );
    }
}
