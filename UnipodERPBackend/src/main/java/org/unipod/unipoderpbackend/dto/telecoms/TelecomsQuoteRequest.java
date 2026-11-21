package org.unipod.unipoderpbackend.dto.telecoms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TelecomsQuoteRequest(
        @NotNull @Positive @Digits(integer = 19, fraction = 0) BigDecimal amount,
        @NotBlank @Size(max = 64) String priceListItemId,
        @NotNull @Positive BigDecimal priceQuantity,
        @NotNull LocalDate expectedCompletion,
        @NotNull @Valid TelecomsRequestPayload.Estimate estimate
) {
}
