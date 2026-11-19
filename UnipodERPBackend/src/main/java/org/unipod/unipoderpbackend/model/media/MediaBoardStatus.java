package org.unipod.unipoderpbackend.model.media;

import java.util.Optional;

/**
 * The four stages the media board shows (D-039). A media request is stored with two more
 * (COMPLETED, CANCELLED) that the board does not have a column for.
 */
public enum MediaBoardStatus {
    REQUESTED,
    SCHEDULED,
    IN_PRODUCTION,
    DELIVERED;

    /** How a stored status appears on the board; a cancelled request does not appear at all. */
    public static Optional<MediaBoardStatus> shown(String storedStatus) {
        return switch (storedStatus) {
            case "REQUESTED" -> Optional.of(REQUESTED);
            case "SCHEDULED" -> Optional.of(SCHEDULED);
            case "IN_PRODUCTION" -> Optional.of(IN_PRODUCTION);
            // A closed request was delivered first, so its column is Delivered.
            case "DELIVERED", "COMPLETED" -> Optional.of(DELIVERED);
            default -> Optional.empty();
        };
    }
}
