package org.unipod.unipoderpbackend.dto.telecoms;

import java.util.UUID;

public record TelecomsPriceListItemResponse(
        UUID id,
        String label,
        String unit,
        long unitPrice
) {
}
