package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TextileQuoteRequest(
        @NotNull List<@NotNull @Valid TextileMaterialLine> materials,
        @NotNull UUID priceListItemId,
        @NotNull @DecimalMin("1") @DecimalMax("2147483647") @Digits(integer = 10, fraction = 0) BigDecimal priceQuantity,
        @NotNull LocalDate expectedCompletion
) {}
