package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TextileProductionRequest(
        @Size(max = 1000) String materialUsed,
        UUID machineId,
        @Size(max = 255) String staff,
        @Size(max = 2000) String notes
) {}
