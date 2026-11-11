package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MissingItemsRequest(
        @NotEmpty @Valid List<@NotNull MissingItemRequest> items
) {
}
