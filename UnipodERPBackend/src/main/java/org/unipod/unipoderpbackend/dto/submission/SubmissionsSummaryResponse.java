package org.unipod.unipoderpbackend.dto.submission;

import java.util.List;

/**
 * The submitter's home summary: counts for each tab and the
 * "waiting for you" banners that need the submitter to act.
 */
public record SubmissionsSummaryResponse(
        List<TabCount> tabs,
        List<WaitingBanner> waitingForYou
) {
    public record TabCount(String key, String label, long count) {
    }

    public record WaitingBanner(
            String type,
            String title,
            String message,
            long count,
            String href
    ) {
    }
}
