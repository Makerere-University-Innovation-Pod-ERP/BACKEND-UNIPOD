package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;

public record TextileOrderRequest(
        @NotBlank @Size(max = 160) @NoNullCharacters String customerName,
        @Size(max = 40) @NoNullCharacters String customerType,
        @Size(max = 240) @NoNullCharacters String whatIsWanted,
        @Size(max = 120) @NoNullCharacters String quantity,
        @Size(max = 500) @NoNullCharacters String sizeDesign,
        LocalDate dateNeeded,
        boolean rulesAccepted,
        boolean submit
) {}
