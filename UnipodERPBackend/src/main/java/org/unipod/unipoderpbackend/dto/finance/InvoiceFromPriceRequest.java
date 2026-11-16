package org.unipod.unipoderpbackend.dto.finance;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.finance.InvoiceSourceType;

public record InvoiceFromPriceRequest(
        @NotBlank @Email @Size(max = 254) String customerEmail,
        @Size(max = 255) String customerName,
        @Min(1) @Max(1000) Integer quantity,
        @NotNull InvoiceSourceType sourceType,
        @Size(max = 64) String sourceId
) {
}
