package org.unipod.unipoderpbackend.service.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.operations.DepartmentOrderStageRequest;
import org.unipod.unipoderpbackend.exception.operations.InvalidOperationsRequestException;
import org.unipod.unipoderpbackend.exception.operations.OperationsConflictException;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives the orders code through its real entry point (moving a paid order to a completion stage with
 * `materials_used`), because it now records stock through `StockItem.record`.
 */
@SpringBootTest
@Transactional
class DepartmentOrderStockDeductionTest {
    @Autowired
    private DepartmentOrderService orders;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void completingAPaidOrderDeductsStockAndRecordsATypedMovementWithTheOrderCode() throws Exception {
        String sku = "DEDUCT-" + UUID.randomUUID().toString().substring(0, 8);
        UUID item = stockItem(sku, "10");
        UUID order = paidOrder("CNC-" + UUID.randomUUID().toString().substring(0, 8));

        orders.changeStage("cnc", order, stage("[{\"sku\": \"" + sku + "\", \"qty\": 4}]"), "staff@test.local");
        entityManager.flush();

        assertEquals(0, new BigDecimal("6").compareTo(balance(item)));
        Map<String, Object> movement = jdbc.queryForMap("SELECT movement_type, delta, balance_after, reference_id, "
                + "reference_code, department_order_id, reason, created_by FROM stock_movements WHERE stock_item_id = ?", item);
        assertEquals("USED_ON_ORDER", movement.get("movement_type"));
        assertEquals(0, new BigDecimal("-4").compareTo((BigDecimal) movement.get("delta")));
        assertEquals(0, new BigDecimal("6").compareTo((BigDecimal) movement.get("balance_after")));
        assertEquals(order, movement.get("department_order_id"));
        assertEquals(order.toString(), movement.get("reference_id"));
        assertEquals(jdbc.queryForObject("SELECT code FROM department_orders WHERE id = ?", String.class, order),
                movement.get("reference_code"));
        assertEquals("staff@test.local", movement.get("created_by"));
    }

    @Test
    void aFractionalQuantityIsDeductedAsGivenNotRoundedDown() throws Exception {
        String sku = "FRAC-" + UUID.randomUUID().toString().substring(0, 8);
        UUID item = stockItem(sku, "10");
        UUID order = paidOrder("CNC-" + UUID.randomUUID().toString().substring(0, 8));

        orders.changeStage("cnc", order, stage("[{\"sku\": \"" + sku + "\", \"quantity\": \"1.5\"}]"), "staff@test.local");

        assertEquals(0, new BigDecimal("8.5").compareTo(balance(item)));
    }

    @Test
    void nullQtyFallsBackToQuantity() throws Exception {
        String sku = "FALLBACK-" + UUID.randomUUID().toString().substring(0, 8);
        UUID item = stockItem(sku, "10");
        UUID order = paidOrder("CNC-" + UUID.randomUUID().toString().substring(0, 8));

        orders.changeStage("cnc", order,
                stage("[{\"sku\": \"" + sku + "\", \"qty\": null, \"quantity\": 3}]"), "staff@test.local");

        assertEquals(0, new BigDecimal("7").compareTo(balance(item)));
    }

    @Test
    void completingTheSameOrderAgainDoesNotDeductItsMaterialsAgain() throws Exception {
        String sku = "TWICE-" + UUID.randomUUID().toString().substring(0, 8);
        UUID item = stockItem(sku, "10");
        UUID order = paidOrder("CNC-" + UUID.randomUUID().toString().substring(0, 8));
        String materials = "[{\"sku\": \"" + sku + "\", \"qty\": 4}]";

        orders.changeStage("cnc", order, stage(materials), "staff@test.local");
        orders.changeStage("cnc", order, stage(materials), "staff@test.local");

        assertEquals(0, new BigDecimal("6").compareTo(balance(item)));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE stock_item_id = ?", Long.class, item));
    }

    @Test
    void movingFromOneCompletionStageToAnotherDoesNotDeductAgain() throws Exception {
        String sku = "NEXT-" + UUID.randomUUID().toString().substring(0, 8);
        UUID item = stockItem(sku, "10");
        UUID order = paidOrder("CNC-" + UUID.randomUUID().toString().substring(0, 8));
        String materials = "[{\"sku\": \"" + sku + "\", \"qty\": 4}]";

        orders.changeStage("cnc", order, stage(materials), "staff@test.local");
        orders.changeStage("cnc", order, new DepartmentOrderStageRequest("COLLECTED", mapper.readTree(materials), null, null, null, null),
                "staff@test.local");

        assertEquals(0, new BigDecimal("6").compareTo(balance(item)));
    }

    @Test
    void notEnoughStockIsStillAConflictAndLeavesTheBalanceAlone() throws Exception {
        String sku = "SHORT-" + UUID.randomUUID().toString().substring(0, 8);
        UUID item = stockItem(sku, "3");
        UUID order = paidOrder("CNC-" + UUID.randomUUID().toString().substring(0, 8));

        OperationsConflictException failure = assertThrows(OperationsConflictException.class,
                () -> orders.changeStage("cnc", order, stage("[{\"sku\": \"" + sku + "\", \"qty\": 4}]"), "staff@test.local"));

        assertEquals("STOCK_INSUFFICIENT", failure.getCode());
        assertEquals(0, new BigDecimal("3").compareTo(balance(item)));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE stock_item_id = ?", Long.class, item));
    }

    @Test
    void anUnknownSkuIsStillABadRequest() throws Exception {
        UUID order = paidOrder("CNC-" + UUID.randomUUID().toString().substring(0, 8));

        assertThrows(InvalidOperationsRequestException.class,
                () -> orders.changeStage("cnc", order, stage("[{\"sku\": \"NO-SUCH-SKU\", \"qty\": 1}]"), "staff@test.local"));
    }

    private DepartmentOrderStageRequest stage(String materialsJson) throws Exception {
        return new DepartmentOrderStageRequest("READY", mapper.readTree(materialsJson), null, null, null, null);
    }

    private BigDecimal balance(UUID item) {
        entityManager.flush();
        entityManager.clear();
        return jdbc.queryForObject("SELECT quantity_on_hand FROM stock_items WHERE id = ?", BigDecimal.class, item);
    }

    private UUID stockItem(String sku, String quantity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO stock_items (id, sku, name, department, quantity_on_hand, updated_at) "
                + "VALUES (?, ?, 'Deduction item', 'CNC', ?::numeric, ?)", id, sku, quantity, Timestamp.from(Instant.now()));
        return id;
    }

    private UUID paidOrder(String code) {
        Timestamp now = Timestamp.from(Instant.now());
        UUID invoice = UUID.randomUUID();
        jdbc.update("INSERT INTO invoices (id, number, customer_email, description, amount, source_type, status, issued_by, "
                + "created_at, updated_at) VALUES (?, ?, 'customer@test.local', 'Job', 1000, 'JOB', 'PAID', 'staff@test.local', ?, ?)",
                invoice, "INV-" + code, now, now);
        UUID order = UUID.randomUUID();
        jdbc.update("INSERT INTO department_orders (id, code, department, customer_id, title, quantity, stage, invoice_id, "
                + "invoice_status, created_by, created_at, updated_at) VALUES (?, ?, 'CNC', 'customer@test.local', 'Shelf', 1, "
                + "'IN_PRODUCTION', ?, 'PAID', 'staff@test.local', ?, ?)", order, code, invoice, now, now);
        return order;
    }
}
