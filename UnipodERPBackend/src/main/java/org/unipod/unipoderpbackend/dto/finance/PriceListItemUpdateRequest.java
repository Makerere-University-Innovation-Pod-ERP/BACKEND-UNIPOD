package org.unipod.unipoderpbackend.dto.finance;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.unipod.unipoderpbackend.model.finance.PriceItemStatus;
import org.unipod.unipoderpbackend.model.finance.PriceUnit;

public record PriceListItemUpdateRequest(
        @Size(min = 1, max = 200) String service,
        PriceUnit unit,
        @Positive Long priceUgx,
        LocalDate effectiveFrom,
        PriceItemStatus status
) {
}
