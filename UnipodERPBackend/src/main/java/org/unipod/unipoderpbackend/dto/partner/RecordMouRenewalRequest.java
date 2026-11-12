package org.unipod.unipoderpbackend.dto.partner;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RecordMouRenewalRequest(
        @NotNull LocalDate renewalDate,
        @Size(max = 500) String documentReference) {
}
