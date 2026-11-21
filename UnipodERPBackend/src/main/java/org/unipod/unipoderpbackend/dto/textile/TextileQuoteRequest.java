package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TextileQuoteRequest(
        @NotNull @Valid List<TextileMaterialLine> materials,
        @NotNull UUID priceListItemId,
        @NotNull @DecimalMin("1") @Digits(integer = 10, fraction = 2) BigDecimal priceQuantity,
        @NotNull LocalDate expectedCompletion
) {}
