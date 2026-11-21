package org.unipod.unipoderpbackend.dto.telecoms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TelecomsRequestPayload(
        @Size(max = 200) String customerName,
        @Pattern(regexp = "Student|Public|Company|Research Group|Internal")
        @Size(max = 40) String customerType,
        @Size(max = 2000) String serviceWanted,
        @Size(max = 1000) String equipmentInvolved,
        LocalDate preferredDate,
        boolean rulesAccepted,
        @Valid Estimate estimate,
        Boolean draft
) {
    public record Estimate(
            @NotNull @Size(max = 100) List<@NotNull @Valid PartEstimate> parts,
            @NotNull @Size(max = 64) String priceListItemId,
            @NotNull @Positive BigDecimal priceQuantity
    ) {
        public record PartEstimate(
                @NotNull @Size(min = 1, max = 64) String itemId,
                @NotNull @Positive BigDecimal quantity
        ) {
        }
    }
}
