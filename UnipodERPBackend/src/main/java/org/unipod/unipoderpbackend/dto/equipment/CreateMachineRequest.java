package org.unipod.unipoderpbackend.dto.equipment;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.equipment.Machine;
import org.unipod.unipoderpbackend.model.equipment.MachineState;

/** Adds a machine to the workshop list. The frontend has no screen for this yet; staff need it to fill the list. */
public record CreateMachineRequest(
        @NotBlank @Size(max = Machine.NAME_MAX) @NoNullCharacters String name,
        MachineState state,
        @Size(max = Machine.NOTE_MAX) @NoNullCharacters String note
) {
}
