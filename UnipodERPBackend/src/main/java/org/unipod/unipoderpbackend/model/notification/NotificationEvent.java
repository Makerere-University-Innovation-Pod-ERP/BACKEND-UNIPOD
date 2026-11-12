package org.unipod.unipoderpbackend.model.notification;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a module hands to {@code NotificationService.publish(...)}. Not stored itself;
 * the service turns it into one {@link Notification} per recipient.
 *
 * @param type       which NT1 event this is
 * @param recipients emails of the people to tell (the JWT subject of each)
 * @param title      the short line shown in the bell, at most 200 characters
 * @param body       optional longer text
 * @param entityType what the notification is about, e.g. "submission" (optional)
 * @param entityId   which record, so the frontend can link to it (optional)
 */
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
