package org.unipod.unipoderpbackend.controller.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.unipod.unipoderpbackend.controller.equipment.ConcurrentRunner;

/**
 * Not transactional: the item's row lock, not the test transaction, must keep stock from going negative. Rows are
 * removed afterwards; audit entries stay because the audit log is append-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StockConcurrencyTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> items = new ArrayList<>();

    @AfterEach
    void removeRows() {
        for (UUID id : items) {
            jdbc.update("DELETE FROM stock_movements WHERE stock_item_id = ?", id);
            jdbc.update("DELETE FROM stock_items WHERE id = ?", id);
        }
    }

    @Test
    void parallelUsageThatWouldGoNegativeLetsOnlyWhatIsInStockThrough() throws Exception {
        UUID item = item("TELECOMS", "10");
        List<Callable<Integer>> uses = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            uses.add(record("/telecoms/stock/movements", item, "USED_ON_REQUEST", "2", "\"requestId\": \"REQ-" + i + "\""));
        }

        List<Integer> statuses = ConcurrentRunner.runTogether(uses);

        assertEquals(5, statuses.stream().filter(status -> status == 201).count());
        assertEquals(3, statuses.stream().filter(status -> status == 422).count());
        assertEquals(0, balance(item).signum());
        assertEquals(5L, movementCount(item));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE stock_item_id = ? AND balance_after < 0", Long.class, item));
    }

    @Test
    void textileUsageIsProtectedTheSameWay() throws Exception {
        UUID item = item("TEXTILE", "5");
        List<Callable<Integer>> uses = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            uses.add(record("/textile/stock/movements", item, "USED_ON_ORDER", "2", "\"orderId\": \"TXO-" + i + "\""));
        }

        List<Integer> statuses = ConcurrentRunner.runTogether(uses);

        assertEquals(2, statuses.stream().filter(status -> status == 201).count());
        assertEquals(2, statuses.stream().filter(status -> status == 422).count());
        assertEquals(new BigDecimal("1.00"), balance(item));
    }

    @Test
    void receivingAndUsingTogetherAlwaysEndAtTheSumOfWhatWentThrough() throws Exception {
        UUID item = item("TELECOMS", "0");
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(record("/telecoms/stock/movements", item, "RECEIVED", "1", "\"note\": \"delivery " + i + "\""));
            calls.add(record("/telecoms/stock/movements", item, "USED_ON_REQUEST", "1", "\"requestId\": \"REQ-" + i + "\""));
        }

        List<Integer> statuses = ConcurrentRunner.runTogether(calls);

        long received = 0;
        long used = 0;
        for (int i = 0; i < statuses.size(); i++) {
            assertTrue(statuses.get(i) == 201 || statuses.get(i) == 422, statuses.toString());
            if (statuses.get(i) == 201) {
                if (i % 2 == 0) {
                    received++;
                } else {
                    used++;
                }
            }
        }
        assertEquals(6, received);
        assertEquals(BigDecimal.valueOf(received - used).setScale(2), balance(item));
        assertTrue(balance(item).signum() >= 0);
    }

    private Callable<Integer> record(String path, UUID item, String type, String quantity, String extra) {
        return () -> mockMvc.perform(MockMvcRequestBuilders.post(path)
                        .with(user("admin-" + UUID.randomUUID() + "@test.local").roles("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\": \"%s\", \"type\": \"%s\", \"quantity\": %s, %s}".formatted(item, type, quantity, extra)))
                .andReturn().getResponse().getStatus();
    }

    private UUID item(String department, String quantity) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO stock_items (id, sku, name, department, quantity_on_hand, unit, minimum, location, updated_at) "
                        + "VALUES (?, ?, 'Race stock', ?, ?::numeric, 'pcs', 0, 'Store', ?)",
                id, "RACE-" + id, department, quantity, Timestamp.from(Instant.now()));
        items.add(id);
        return id;
    }

    private BigDecimal balance(UUID item) {
        return jdbc.queryForObject("SELECT quantity_on_hand FROM stock_items WHERE id = ?", BigDecimal.class, item);
    }

    private long movementCount(UUID item) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE stock_item_id = ?", Long.class, item);
        return count == null ? 0 : count;
    }
}
