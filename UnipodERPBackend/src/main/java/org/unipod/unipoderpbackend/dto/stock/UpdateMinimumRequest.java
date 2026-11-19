package org.unipod.unipoderpbackend.dto.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** The only thing the stock screens change on an item: its low-stock minimum. */
public record UpdateMinimumRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal minimum
) {
}
