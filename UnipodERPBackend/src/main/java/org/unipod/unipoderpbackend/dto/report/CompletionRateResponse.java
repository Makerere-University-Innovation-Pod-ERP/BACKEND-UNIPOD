package org.unipod.unipoderpbackend.dto.report;

public record CompletionRateResponse(
        long submitted,
        long completed,
        long closed,
        double completionRate,
        double closedRate
) {
}
