package org.unipod.unipoderpbackend.dto.submission;

import java.util.List;
import java.util.Map;

public record SubmitterSummaryResponse(
        Map<String, Long> tabCounts,
        List<WaitingForYou> waitingForYou
) {
    public record WaitingForYou(String type, long count, String message) {
    }
}
