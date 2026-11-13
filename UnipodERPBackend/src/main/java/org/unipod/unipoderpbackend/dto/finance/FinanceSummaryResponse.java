package org.unipod.unipoderpbackend.dto.finance;

public record FinanceSummaryResponse(
        long totalInvoices,
        long waitingForProof,
        long waitingForCheck,
        long paid,
        long receipted,
        long outstandingUgx,
        long collectedUgx
) {
}
