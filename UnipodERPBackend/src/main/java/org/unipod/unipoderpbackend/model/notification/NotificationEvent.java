package org.unipod.unipoderpbackend.model.notification;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record NotificationEvent(
        NotificationType type,
        Set<String> recipients,
        String title,
        String body,
        String entityType,
        UUID entityId
) {
    public NotificationEvent {
        Objects.requireNonNull(type, "type");
        recipients = recipients == null ? Set.of() : recipients.stream()
                .filter(Objects::nonNull)
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("A notification needs at least one recipient");
        }
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("title must be 1 to 200 characters");
        }
    }
}
