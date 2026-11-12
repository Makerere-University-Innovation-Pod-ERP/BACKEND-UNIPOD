package org.unipod.unipoderpbackend.dto.notification;

/** How many notifications were marked read by POST /notifications/read-all. */
public record ReadAllResponse(int marked) {
}
