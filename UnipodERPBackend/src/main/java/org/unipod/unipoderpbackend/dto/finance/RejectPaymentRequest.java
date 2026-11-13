package org.unipod.unipoderpbackend.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RejectPaymentRequest(
        @NotNull UUID proofId,
        @NotBlank @Size(max = 500) String reason
) {
}
