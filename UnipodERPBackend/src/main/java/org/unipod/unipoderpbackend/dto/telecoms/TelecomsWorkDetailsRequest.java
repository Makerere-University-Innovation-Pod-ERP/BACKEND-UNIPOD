package org.unipod.unipoderpbackend.dto.telecoms;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;

public record TelecomsWorkDetailsRequest(
        @NotNull @Size(max = 200) @NoNullCharacters String technician,
        @NotNull @Size(max = 2000) @NoNullCharacters String partsUsed,
        @NotNull @Size(max = 200) @NoNullCharacters String timeSpent,
        @NotNull @Size(max = 5000) @NoNullCharacters String notes
) {
}
