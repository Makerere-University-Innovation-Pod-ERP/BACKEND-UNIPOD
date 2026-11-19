package org.unipod.unipoderpbackend.dto.equipment;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.equipment.FaultKind;
import org.unipod.unipoderpbackend.model.equipment.Machine;

/** The JSON part of a machine report. A missing kind means FAULT. */
public record ReportMachineRequest(
        FaultKind kind,
        @NotBlank @Size(max = Machine.NOTE_MAX) @NoNullCharacters String description
) {
}
