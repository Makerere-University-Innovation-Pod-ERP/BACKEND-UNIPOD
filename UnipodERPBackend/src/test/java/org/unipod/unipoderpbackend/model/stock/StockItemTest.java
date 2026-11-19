package org.unipod.unipoderpbackend.model.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.exception.stock.InvalidStockException;
import org.unipod.unipoderpbackend.model.operations.StockItem;
import org.unipod.unipoderpbackend.model.operations.StockMovement;

class StockItemTest {
    private static final Instant NOW = Instant.parse("2026-11-19T09:00:00Z");

    @Test
    void receivedStockAddsToTheBalance() {
        StockItem item = item("10", "5");

        StockMovement movement = item.record(draft(StockMovementType.RECEIVED, "2.5", null, null), "a@test.local", NOW);

        assertEquals(new BigDecimal("12.50"), item.getQuantityOnHand());
        assertEquals(new BigDecimal("2.50"), movement.getDelta());
        assertEquals(new BigDecimal("12.50"), movement.getBalanceAfter());
        assertEquals(StockMovementType.RECEIVED, movement.getMovementType());
        assertEquals("a@test.local", movement.getCreatedBy());
    }

    @Test
    void usedStockSubtractsAndKeepsTheReference() {
        StockItem item = item("10", "5");

        StockMovement movement = item.record(draft(StockMovementType.USED_ON_ORDER, "4", null, "ORD-7"), "a@test.local", NOW);

        assertEquals(new BigDecimal("6.00"), item.getQuantityOnHand());
        assertEquals(new BigDecimal("-4.00"), movement.getDelta());
        assertEquals("ORD-7", movement.getReferenceId());
    }

    @Test
    void usingMoreThanIsInStockIsRefusedAndChangesNothing() {
        StockItem item = item("3", "0");

        InvalidStockException failure = assertThrows(InvalidStockException.class,
                () -> item.record(draft(StockMovementType.USED_ON_REQUEST, "3.01", null, "REQ-1"), "a@test.local", NOW));

        assertTrue(failure.getReason().contains("below zero"), failure.getReason());
        assertEquals(new BigDecimal("3.00"), item.getQuantityOnHand());
    }

    @Test
    void usingExactlyWhatIsInStockEmptiesIt() {
        StockItem item = item("3", "1");

        item.record(draft(StockMovementType.USED_ON_REQUEST, "3", null, "REQ-1"), "a@test.local", NOW);

        assertEquals(0, item.getQuantityOnHand().signum());
        assertTrue(item.isLow());
    }

    @Test
    void correctionMayBeNegativeButNeedsANote() {
        StockItem item = item("10", "0");

        assertThrows(InvalidStockException.class,
                () -> item.record(draft(StockMovementType.CORRECTION, "-2", null, null), "a@test.local", NOW));
        StockMovement movement = item.record(draft(StockMovementType.CORRECTION, "-2", "Counted again", null), "a@test.local", NOW);

        assertEquals(new BigDecimal("8.00"), item.getQuantityOnHand());
        assertEquals(new BigDecimal("-2.00"), movement.getDelta());
        assertEquals("Counted again", movement.getReason());
    }

    @Test
    void receivedAndUsedQuantitiesMustBePositive() {
        StockItem item = item("10", "0");

        assertThrows(InvalidStockException.class,
                () -> item.record(draft(StockMovementType.RECEIVED, "-1", null, null), "a@test.local", NOW));
        assertThrows(InvalidStockException.class,
                () -> item.record(draft(StockMovementType.USED_ON_ORDER, "-1", null, "ORD-1"), "a@test.local", NOW));
        assertThrows(InvalidStockException.class,
                () -> item.record(draft(StockMovementType.RECEIVED, "0", null, null), "a@test.local", NOW));
    }

    @Test
    void usageNeedsTheRequestOrOrderItWasUsedOn() {
        StockItem item = item("10", "0");

        assertThrows(InvalidStockException.class,
                () -> item.record(draft(StockMovementType.USED_ON_ORDER, "1", null, " "), "a@test.local", NOW));
        assertThrows(InvalidStockException.class,
                () -> item.record(draft(StockMovementType.USED_ON_REQUEST, "1", null, null), "a@test.local", NOW));
    }

    @Test
    void lowMeansStrictlyBelowTheMinimum() {
        StockItem item = item("5", "5");
        assertFalse(item.isLow());

        item.record(draft(StockMovementType.USED_ON_ORDER, "0.01", null, "ORD-1"), "a@test.local", NOW);

        assertTrue(item.isLow());
    }

    @Test
    void aBalanceTheStockListCannotHoldIsRefused() {
        StockItem item = item("9999999999", "0");

        assertThrows(InvalidStockException.class,
                () -> item.record(draft(StockMovementType.RECEIVED, "1.00", null, null), "a@test.local", NOW));
        item.record(draft(StockMovementType.RECEIVED, "0.99", null, null), "a@test.local", NOW);
    }

    @Test
    void minimumCannotBeNegative() {
        StockItem item = item("5", "5");

        assertThrows(InvalidStockException.class, () -> item.changeMinimum(new BigDecimal("-1"), NOW));
        item.changeMinimum(new BigDecimal("7.5"), NOW);

        assertEquals(new BigDecimal("7.50"), item.getMinimum());
    }

    private static StockItem item(String quantity, String minimum) {
        return new StockItem(UUID.randomUUID(), "SKU-" + UUID.randomUUID(), "Cotton fabric", "TEXTILE",
                new BigDecimal(quantity), "metres", new BigDecimal(minimum), "Store", NOW);
    }

    private static StockMovementDraft draft(StockMovementType type, String quantity, String note, String reference) {
        return new StockMovementDraft(type, new BigDecimal(quantity), note, reference, null, null);
    }
}
