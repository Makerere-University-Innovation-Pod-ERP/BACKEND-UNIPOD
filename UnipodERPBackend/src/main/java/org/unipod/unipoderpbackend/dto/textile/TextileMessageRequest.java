package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TextileMessageRequest(
        @NotBlank @Size(max = 1000) String message,
        @Size(max = 1000) String reason,
        @Size(max = 200) String collectedBy
) {}
