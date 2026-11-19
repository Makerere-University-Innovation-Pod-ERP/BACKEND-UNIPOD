package org.unipod.unipoderpbackend.dto.equipment;

import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.model.equipment.Machine;

public record MarkMachineWorkingRequest(@Size(max = Machine.NOTE_MAX) @NoNullCharacters String note) {
}
