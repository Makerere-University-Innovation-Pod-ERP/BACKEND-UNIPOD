package org.unipod.unipoderpbackend.dto.operations;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DepartmentOrderRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull JsonNode specs,
        @Min(1) Integer quantity,
        LocalDate date_needed,
        @Size(max = 32) String accepted_rules_version
) {}

