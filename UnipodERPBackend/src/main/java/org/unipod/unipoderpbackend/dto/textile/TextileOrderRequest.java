package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record TextileOrderRequest(
        @NotBlank @Size(max = 160) String customerName,
        @Size(max = 40) String customerType,
        @Size(max = 240) String whatIsWanted,
        @Size(max = 120) String quantity,
        @Size(max = 500) String sizeDesign,
        LocalDate dateNeeded,
        boolean rulesAccepted,
        boolean submit
) {}
