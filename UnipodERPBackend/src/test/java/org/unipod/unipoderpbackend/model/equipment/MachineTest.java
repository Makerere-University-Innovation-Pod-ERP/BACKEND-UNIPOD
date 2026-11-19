package org.unipod.unipoderpbackend.model.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.exception.equipment.EquipmentConflictException;
import org.unipod.unipoderpbackend.exception.equipment.InvalidEquipmentException;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

class MachineTest {
    private static final Instant NOW = Instant.parse("2026-11-19T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-19T10:00:00Z");

    @Test
    void aFaultFlagsAWorkingMachineAndKeepsTheDescription() {
        Machine machine = machine(MachineState.WORKING);

        machine.report(FaultKind.FAULT, " Needle keeps snapping ", LATER);

        assertEquals(MachineState.FAULT_REPORTED, machine.getState());
        assertEquals("Needle keeps snapping", machine.getNote());
        assertEquals(LATER, machine.getUpdatedAt());
    }

    @Test
    void aServiceRequestOnlyFlagsAWorkingMachine() {
        Machine working = machine(MachineState.WORKING);
        working.report(FaultKind.SERVICE, "Annual service", LATER);
        assertEquals(MachineState.SERVICE_DUE, working.getState());

        Machine faulty = machine(MachineState.FAULT_REPORTED);
        faulty.report(FaultKind.SERVICE, "Annual service", LATER);
        assertEquals(MachineState.FAULT_REPORTED, faulty.getState());
    }

    @Test
    void aFaultDoesNotInterruptARepair() {
        Machine machine = machine(MachineState.BEING_REPAIRED);

        machine.report(FaultKind.FAULT, "Another noise", LATER);

        assertEquals(MachineState.BEING_REPAIRED, machine.getState());
    }

    @Test
    void aReportNeedsADescription() {
        Machine machine = machine(MachineState.WORKING);

        assertThrows(InvalidEquipmentException.class, () -> machine.report(FaultKind.FAULT, "  ", LATER));
    }

    @Test
    void markWorkingResetsTheStateAndUsesTheNoteOrADefault() {
        Machine machine = machine(MachineState.FAULT_REPORTED);

        machine.markWorking("Replaced the belt", LATER);

        assertEquals(MachineState.WORKING, machine.getState());
        assertEquals("Replaced the belt", machine.getNote());

        Machine other = machine(MachineState.SERVICE_DUE);
        other.markWorking(null, LATER);
        assertEquals("Marked working.", other.getNote());
    }

    @Test
    void markingAWorkingMachineWorkingIsAConflict() {
        Machine machine = machine(MachineState.WORKING);

        assertThrows(EquipmentConflictException.class, () -> machine.markWorking(null, LATER));
    }

    private static Machine machine(MachineState state) {
        return new Machine(UUID.randomUUID(), DepartmentCode.TEXTILE, "Industrial sewing machine", state, "", NOW);
    }
}
