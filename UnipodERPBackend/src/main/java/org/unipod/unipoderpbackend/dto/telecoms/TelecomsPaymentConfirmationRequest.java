package org.unipod.unipoderpbackend.dto.telecoms;

import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;

public record TelecomsPaymentConfirmationRequest(
        @Size(max = 100) @NoNullCharacters String paymentReference
) {
}
