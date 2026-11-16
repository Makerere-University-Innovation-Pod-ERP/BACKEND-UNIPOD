package org.unipod.unipoderpbackend.dto.operations;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record MediaRequestCreateRequest(
        UUID briefId,
        UUID departmentOrderId,
        @NotBlank String submitterId,
        @NotBlank String title
) {}
