package org.unipod.unipoderpbackend.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.finance.PriceUnit;

public record PriceListItemRequest(
        @NotNull UUID departmentId,
        @NotBlank @Size(max = 200) String service,
        @NotNull PriceUnit unit,
        @NotNull @Positive Long priceUgx,
        LocalDate effectiveFrom
) {
}
