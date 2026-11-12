package org.unipod.unipoderpbackend.dto.partner;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.unipod.unipoderpbackend.model.partner.PartnerType;

/** Every field except {@code version} is optional; an omitted field is left unchanged. */
public record UpdatePartnerRequest(
        @NotNull Long version,
        @Size(max = 255) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String name,
        PartnerType type,
        LocalDate renewalDate,
        @Size(max = 255) String contactPerson,
        @Email @Size(max = 254) String contactEmail,
        @Size(max = 2000) String notes) {
}
