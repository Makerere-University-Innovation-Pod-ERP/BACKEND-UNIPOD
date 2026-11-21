package org.unipod.unipoderpbackend.dto.telecoms;

import jakarta.validation.constraints.NotNull;
import org.unipod.unipoderpbackend.model.telecoms.TelecomsRequestStatus;

public record TelecomsRequestStatusRequest(
        @NotNull TelecomsRequestStatus toStatus
) {
}
