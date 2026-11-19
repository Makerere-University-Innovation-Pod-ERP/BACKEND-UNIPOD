package org.unipod.unipoderpbackend.dto.equipment;

import java.time.Instant;
import org.unipod.unipoderpbackend.model.equipment.Machine;
import org.unipod.unipoderpbackend.model.equipment.MachineState;

/** A workshop machine as the textile screens read it (`services/textile.js`, machineSchema). */
public record MachineResponse(String id, String name, MachineState state, String note, Instant updatedAt) {
    public static MachineResponse from(Machine machine) {
        return new MachineResponse(machine.getId().toString(), machine.getName(), machine.getState(),
                machine.getNote(), machine.getUpdatedAt());
    }
}
