package org.unipod.unipoderpbackend.dto.operations;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.validation.HttpsUrl;

public record CreateServiceCatalogueItemRequest(
        @NotNull DepartmentCode department,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull @PositiveOrZero Long price,
        @Pattern(regexp = "(?i)^[A-Z]{3}$") String currency,
        @Size(max = 120) String turnaroundEstimate,
        @HttpsUrl @Size(max = 500) String whatsappGroupUrl,
        boolean requiresBrief,
        Boolean active,
        @Min(0) Integer sortOrder
) {
}
