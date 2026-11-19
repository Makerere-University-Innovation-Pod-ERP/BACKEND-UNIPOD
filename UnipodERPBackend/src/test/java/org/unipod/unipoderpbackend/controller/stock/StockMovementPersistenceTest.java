package org.unipod.unipoderpbackend.controller.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.operations.StockItem;
import org.unipod.unipoderpbackend.model.operations.StockMovement;
import org.unipod.unipoderpbackend.model.stock.StockMovementDraft;
import org.unipod.unipoderpbackend.model.stock.StockMovementType;
import org.unipod.unipoderpbackend.repository.operations.StockItemRepository;
import org.unipod.unipoderpbackend.repository.operations.StockMovementRepository;

/**
 * The orders code deducts materials from stock with the same draft this test uses (type USED_ON_ORDER, the order's
 * id and code as reference, whole-number quantity). It must produce a row the V52 checks accept.
 */
@SpringBootTest
@Transactional
class StockMovementPersistenceTest {
    @Autowired
    private StockItemRepository items;

    @Autowired
    private StockMovementRepository movements;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anOrderDeductionIsStoredAsATypedMovementWithItsBalance() {
        UUID orderId = UUID.randomUUID();
        StockItem item = new StockItem(UUID.randomUUID(), "PERSIST-" + UUID.randomUUID(), "Plywood sheet", "CNC",
                BigDecimal.TEN, "sheets", BigDecimal.ZERO, "Store", Instant.now());
        items.save(item);

        StockMovement movement = item.record(new StockMovementDraft(StockMovementType.USED_ON_ORDER, BigDecimal.valueOf(4),
                "Auto-deducted for order CNC-0001", orderId.toString(), "CNC-0001", null), "staff@test.local", Instant.now());
        movements.save(movement);
        entityManager.flush();
        entityManager.clear();

        assertEquals(0, new BigDecimal("6").compareTo(items.findById(item.getId()).orElseThrow().getQuantityOnHand()));
        StockMovement stored = movements.findById(movement.getId()).orElseThrow();
        assertEquals(StockMovementType.USED_ON_ORDER, stored.getMovementType());
        assertEquals(0, new BigDecimal("-4").compareTo(stored.getDelta()));
        assertEquals(0, new BigDecimal("6").compareTo(stored.getBalanceAfter()));
        assertEquals("CNC-0001", stored.getReferenceCode());
        assertNotNull(stored.getCreatedAt());
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE stock_item_id = ?", Long.class, item.getId()));
    }
}
