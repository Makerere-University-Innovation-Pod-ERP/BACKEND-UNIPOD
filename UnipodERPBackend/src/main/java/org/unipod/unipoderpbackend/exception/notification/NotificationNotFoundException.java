package org.unipod.unipoderpbackend.exception.notification;

import java.util.UUID;

/** Thrown when the notification does not exist OR belongs to someone else. Both look the same on purpose. */
public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(UUID id) {
        super("Notification not found: " + id);
    }
}
