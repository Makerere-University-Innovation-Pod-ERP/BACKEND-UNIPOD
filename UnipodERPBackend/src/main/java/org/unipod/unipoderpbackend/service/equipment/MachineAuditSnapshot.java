package org.unipod.unipoderpbackend.service.equipment;

import java.util.UUID;
import org.unipod.unipoderpbackend.model.equipment.FaultKind;
import org.unipod.unipoderpbackend.model.equipment.Machine;

/** What the audit log keeps about a machine: its state and the report id. Names, notes and descriptions stay out. */
record MachineAuditSnapshot(String department, String state, FaultKind kind, UUID reportId) {
    static MachineAuditSnapshot of(Machine machine) {
        return of(machine, null, null);
    }

    static MachineAuditSnapshot of(Machine machine, FaultKind kind, UUID reportId) {
        return new MachineAuditSnapshot(machine.getDepartment().name(), machine.getState().name(), kind, reportId);
    }
}
