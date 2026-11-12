package org.unipod.unipoderpbackend.dto.partner;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.unipod.unipoderpbackend.model.partner.PartnerType;

public record CreatePartnerRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull PartnerType type,
        LocalDate renewalDate,
        @Size(max = 255) String contactPerson,
        @Email @Size(max = 254) String contactEmail,
        @Size(max = 2000) String notes) {
}
