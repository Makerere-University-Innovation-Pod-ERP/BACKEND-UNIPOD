package org.unipod.unipoderpbackend.dto.finance;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.finance.InvoiceSourceType;

public record CreateInvoiceRequest(
        @NotBlank @Email @Size(max = 254) String customerEmail,
        @Size(max = 255) String customerName,
        @Size(max = 100) String department,
        @NotBlank @Size(max = 1000) String description,
        @Positive long amount,
        @NotNull InvoiceSourceType sourceType,
        @Size(max = 64) String sourceId
) {
}
