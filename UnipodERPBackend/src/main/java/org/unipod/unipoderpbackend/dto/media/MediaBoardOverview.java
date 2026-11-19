package org.unipod.unipoderpbackend.dto.media;

import java.util.List;

/** The frontend's {@code mediaOverviewSchema}: the four counts and the list they were counted from. */
public record MediaBoardOverview(
        long requestedCount,
        long scheduledCount,
        long inProductionCount,
        long deliveredCount,
        List<MediaBoardRequest> requests) {
}
