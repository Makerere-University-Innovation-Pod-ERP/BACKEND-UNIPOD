package org.unipod.unipoderpbackend.dto.telecoms;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TelecomsWorkDetailsRequest(
        @NotNull @Size(max = 200) String technician,
        @NotNull @Size(max = 2000) String partsUsed,
        @NotNull @Size(max = 200) String timeSpent,
        @NotNull @Size(max = 5000) String notes
) {
}
