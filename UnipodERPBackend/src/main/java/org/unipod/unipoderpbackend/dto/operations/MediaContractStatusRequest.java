package org.unipod.unipoderpbackend.dto.operations;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.operations.MediaContractStatus;

public record MediaContractStatusRequest(
        @NotNull MediaContractStatus status,
        UUID signedContractDocId
) {
}
