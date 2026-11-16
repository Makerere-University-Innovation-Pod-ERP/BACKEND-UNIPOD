package org.unipod.unipoderpbackend.dto.finance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.finance.PriceItemStatus;
import org.unipod.unipoderpbackend.model.finance.PriceListItem;
import org.unipod.unipoderpbackend.model.finance.PriceUnit;

public record PriceListItemResponse(
        UUID id,
        UUID departmentId,
        String service,
        PriceUnit unit,
        long priceUgx,
        LocalDate effectiveFrom,
        PriceItemStatus status,
        String proposedBy,
        Instant updatedAt
) {
    public static PriceListItemResponse from(PriceListItem item) {
        return new PriceListItemResponse(
                item.getId(),
                item.getDepartmentId(),
                item.getService(),
                item.getUnit(),
                item.getPriceUgx(),
                item.getEffectiveFrom(),
                item.getStatus(),
                item.getProposedBy(),
                item.getUpdatedAt()
        );
    }

    public static Map<String, Object> snapshot(PriceListItem item) {
        return Map.of(
                "service", item.getService(),
                "unit", item.getUnit().name(),
                "priceUgx", item.getPriceUgx(),
                "effectiveFrom", item.getEffectiveFrom().toString(),
                "status", item.getStatus().name()
        );
    }
}
