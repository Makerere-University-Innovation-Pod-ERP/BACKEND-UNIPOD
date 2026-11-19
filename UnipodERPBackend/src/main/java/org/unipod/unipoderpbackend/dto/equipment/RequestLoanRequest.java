package org.unipod.unipoderpbackend.dto.equipment;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.equipment.EquipmentLoan;

/** A loan request as the lending desk form sends it. An omitted quantity means one unit. */
public record RequestLoanRequest(
        @NotNull UUID equipmentId,
        @Min(1) @Max(1000) Integer quantity,
        @NotBlank @Size(max = EquipmentLoan.BORROWER_MAX) @NoNullCharacters String borrowerName,
        @Size(max = EquipmentLoan.BORROWER_TYPE_MAX) @NoNullCharacters String borrowerType,
        @NotBlank @Size(max = EquipmentLoan.BORROWER_MAX) @NoNullCharacters String borrowerContact,
        @NotNull LocalDate dateOut,
        @NotNull LocalDate dateBack,
        @Size(max = EquipmentLoan.CONDITION_MAX) @NoNullCharacters String outgoingCondition
) {
}
