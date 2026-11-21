package org.unipod.unipoderpbackend.dto.telecoms;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.telecoms.TelecomsPaymentStatus;
import org.unipod.unipoderpbackend.model.telecoms.TelecomsRequestStatus;

public record TelecomsRequestResponse(
        UUID id,
        String code,
        String customerName,
        String customerType,
        String serviceWanted,
        String equipmentInvolved,
        LocalDate preferredDate,
        boolean rulesAccepted,
        TelecomsRequestStatus status,
        Instant createdAt,
        Instant updatedAt,
        Estimate estimate,
        Quote quote,
        Payment payment,
        WorkDetails workDetails,
        List<HistoryEntry> history
) {
    public record PartEstimate(String itemId, BigDecimal quantity) {
    }

    public record Estimate(List<PartEstimate> parts, String priceListItemId, BigDecimal priceQuantity) {
    }

    public record Quote(
            BigDecimal amount,
            String currency,
            String priceListItemId,
            BigDecimal priceQuantity,
            LocalDate expectedCompletion,
            Instant sentAt,
            String sentBy
    ) {
    }

    public record Payment(TelecomsPaymentStatus status, String reference, Instant confirmedAt) {
    }

    public record WorkDetails(String technician, String partsUsed, String timeSpent, String notes) {
    }

    public record HistoryEntry(TelecomsRequestStatus status, String by, Instant at, String note) {
    }
}
