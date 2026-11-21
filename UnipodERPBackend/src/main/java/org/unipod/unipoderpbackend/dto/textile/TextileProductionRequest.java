package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;

public record TextileProductionRequest(
        @Size(max = 1000) @NoNullCharacters String materialUsed,
        UUID machineId,
        @Size(max = 255) @NoNullCharacters String staff,
        @Size(max = 2000) @NoNullCharacters String notes
) {}
