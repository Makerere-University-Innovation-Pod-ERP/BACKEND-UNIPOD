package org.unipod.unipoderpbackend.dto.operations;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record MediaRequestCreateRequest(
        UUID briefId,
        UUID departmentOrderId,
        @NotBlank @Email @Size(max = 254) String submitterId,
        @NotBlank @Size(max = 255) String title
) {}
