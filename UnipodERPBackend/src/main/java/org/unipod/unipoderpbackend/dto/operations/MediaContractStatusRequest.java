package org.unipod.unipoderpbackend.dto.operations;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.operations.MediaContractStatus;

public record MediaContractStatusRequest(
        @NotNull MediaContractStatus status,
        @JsonProperty("signed_contract_doc_id") UUID signedContractDocId
) {
}
