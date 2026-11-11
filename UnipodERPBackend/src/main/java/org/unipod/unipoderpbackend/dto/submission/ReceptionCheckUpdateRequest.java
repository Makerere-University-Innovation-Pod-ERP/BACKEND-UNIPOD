package org.unipod.unipoderpbackend.dto.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReceptionCheckUpdateRequest(
        @NotEmpty @Valid List<ReceptionCheckItemRequest> items
) {
}
