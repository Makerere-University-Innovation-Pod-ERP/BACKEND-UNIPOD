package org.unipod.unipoderpbackend.model.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.exception.equipment.EquipmentConflictException;
import org.unipod.unipoderpbackend.exception.equipment.InvalidEquipmentException;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

class EquipmentItemTest {
    private static final Instant NOW = Instant.parse("2026-11-19T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-19T10:00:00Z");

    @Test
    void startsFullyAvailable() {
        EquipmentItem item = item(4);

        assertEquals(4, item.getAvailableQuantity());
        assertEquals(EquipmentState.AVAILABLE, item.getState());
        assertEquals("", item.getNote());
    }

    @Test
    void rejectsBlankNameOrTagAndNonPositiveTotal() {
        assertThrows(InvalidEquipmentException.class, () -> new EquipmentItem(UUID.randomUUID(), DepartmentCode.TELECOMS,
                " ", "", "TAG-1", 1, "", "", NOW));
        assertThrows(InvalidEquipmentException.class, () -> new EquipmentItem(UUID.randomUUID(), DepartmentCode.TELECOMS,
                "Scope", "", " ", 1, "", "", NOW));
        assertThrows(InvalidEquipmentException.class, () -> new EquipmentItem(UUID.randomUUID(), DepartmentCode.TELECOMS,
                "Scope", "", "TAG-1", 0, "", "", NOW));
    }

    @Test
    void reservingTakesUnitsOutAndTheLastUnitMarksItLoaned() {
        EquipmentItem item = item(2);

        item.reserve(1, LATER);
        assertEquals(1, item.getAvailableQuantity());
        assertEquals(EquipmentState.AVAILABLE, item.getState());
        assertEquals(LATER, item.getUpdatedAt());

        item.reserve(1, LATER);
        assertEquals(0, item.getAvailableQuantity());
        assertEquals(EquipmentState.LOANED, item.getState());
    }

    @Test
    void reservingMoreThanIsAvailableIsAConflictAndChangesNothing() {
        EquipmentItem item = item(2);
        item.reserve(1, NOW);

        EquipmentConflictException failure = assertThrows(EquipmentConflictException.class, () -> item.reserve(2, LATER));

        assertTrue(failure.getReason().contains("Only 1"), failure.getReason());
        assertEquals(1, item.getAvailableQuantity());
    }

    @Test
    void faultyEquipmentCannotBeReserved() {
        EquipmentItem faulty = item(2);
        faulty.reportFault("Screen flickers", NOW);

        assertThrows(EquipmentConflictException.class, () -> faulty.reserve(1, LATER));
        assertEquals(2, faulty.getAvailableQuantity());
    }

    @Test
    void releasingGivesUnitsBackAndFreesALoanedItem() {
        EquipmentItem item = item(2);
        item.reserve(2, NOW);

        item.release(1, LATER);

        assertEquals(1, item.getAvailableQuantity());
        assertEquals(EquipmentState.AVAILABLE, item.getState());
    }

    @Test
    void releasingCannotExceedTheTotal() {
        EquipmentItem item = item(2);

        assertThrows(EquipmentConflictException.class, () -> item.release(1, LATER));
        assertEquals(2, item.getAvailableQuantity());
    }

    @Test
    void releasingKeepsAFaultyItemFaulty() {
        EquipmentItem item = item(2);
        item.reserve(1, NOW);
        item.reportFault("Probe cracked", NOW);

        item.release(1, LATER);

        assertEquals(EquipmentState.FAULTY, item.getState());
        assertEquals(2, item.getAvailableQuantity());
    }

    @Test
    void reportingAFaultFlagsTheItemAndKeepsTheDescriptionAsTheNote() {
        EquipmentItem item = item(2);

        item.reportFault("  Ballast flickers  ", LATER);

        assertEquals(EquipmentState.FAULTY, item.getState());
        assertEquals("Ballast flickers", item.getNote());
        assertThrows(InvalidEquipmentException.class, () -> item.reportFault(" ", LATER));
    }

    @Test
    void markingWorkingClearsAFaultAndKeepsTheStateRightForUnitsStillOut() {
        EquipmentItem free = item(2);
        free.reportFault("Probe cracked", NOW);
        free.markWorking(" Probe replaced ", LATER);
        assertEquals(EquipmentState.AVAILABLE, free.getState());
        assertEquals("Probe replaced", free.getNote());

        EquipmentItem allOut = item(1);
        allOut.reserve(1, NOW);
        allOut.reportFault("Dead", NOW);
        allOut.markWorking(null, LATER);
        assertEquals(EquipmentState.LOANED, allOut.getState());
        assertEquals("Marked working.", allOut.getNote());
    }

    @Test
    void onlyFaultyOrMaintainedEquipmentCanBeMarkedWorking() {
        assertThrows(EquipmentConflictException.class, () -> item(2).markWorking(null, LATER));
    }

    @Test
    void quantityMustBePositive() {
        EquipmentItem item = item(2);

        assertThrows(InvalidEquipmentException.class, () -> item.reserve(0, NOW));
        assertThrows(InvalidEquipmentException.class, () -> item.release(-1, NOW));
    }

    private static EquipmentItem item(int total) {
        return new EquipmentItem(UUID.randomUUID(), DepartmentCode.TELECOMS, "Oscilloscope", "Test gear",
                "TAG-" + UUID.randomUUID(), total, "Bench 1", "", NOW);
    }
}
