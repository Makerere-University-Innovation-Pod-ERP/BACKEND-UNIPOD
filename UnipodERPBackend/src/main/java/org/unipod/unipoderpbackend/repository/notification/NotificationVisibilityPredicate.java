package org.unipod.unipoderpbackend.repository.notification;

import org.unipod.unipoderpbackend.model.notification.NotificationEntityTypes;

final class NotificationVisibilityPredicate {
    static final String QUERY =
            "(:includeBookings = true\n"
            + "     or notification.entityType is null\n"
            + "     or lower(notification.entityType) <> '" + NotificationEntityTypes.BOOKING + "')\n";

    private NotificationVisibilityPredicate() {
    }
}
