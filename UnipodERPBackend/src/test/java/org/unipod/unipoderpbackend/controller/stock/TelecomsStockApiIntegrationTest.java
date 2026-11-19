package org.unipod.unipoderpbackend.controller.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.controller.equipment.EquipmentFixtures;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TelecomsStockApiIntegrationTest {
    private static final String PATH = "/telecoms/stock";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    // The requests write through JPA inside the test transaction; flush so the plain SQL checks can see the rows.
    @Autowired
    private EntityManager entityManager;

    private EquipmentFixtures fixtures() {
        return new EquipmentFixtures(jdbc);
    }

    // ---- list and history ------------------------------------------------------------------------------------

    @Test
    void listsOnlyTelecomsItemsByNameInAnItemsWrapper() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        fixtures.stockItem("TELECOMS", "zener diodes " + suffix, "pcs", "12.5", "5");
        fixtures.stockItem("TELECOMS", "Capacitors " + suffix, "pcs", "140", "50");
        fixtures.stockItem("TEXTILE", "Cotton " + suffix, "metres", "86", "30");

        String body = mockMvc.perform(get(PATH).with(EquipmentFixtures.as("OBSERVER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(body, "$.items[?(@.name =~ /.*" + suffix + "/)].name");
        assertEquals(List.of("Capacitors " + suffix, "zener diodes " + suffix), names);
        List<Map<String, Object>> zener = JsonPath.read(body, "$.items[?(@.name == 'zener diodes " + suffix + "')]");
        assertEquals(12.5, ((Number) zener.get(0).get("quantity")).doubleValue());
        assertEquals(5.0, ((Number) zener.get(0).get("minimum")).doubleValue());
        assertEquals("pcs", zener.get(0).get("unit"));
        assertEquals("Store", zener.get(0).get("location"));
    }

    @Test
    void readingIsRefusedForSubmittersAndAnonymousCallers() throws Exception {
        for (String path : List.of(PATH, PATH + "/movements")) {
            mockMvc.perform(get(path).with(EquipmentFixtures.as("SUBMITTER"))).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void historyListsNewestFirstWithoutPreTypedRowsAndOtherDepartments() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID mine = fixtures.stockItem("TELECOMS", "History item", "pcs", "10", "1");
        UUID other = fixtures.stockItem("TEXTILE", "Other history item", "pcs", "10", "1");
        insertMovement(mine, "RECEIVED", "5", "15", "2026-11-18T09:00:00Z");
        insertMovement(mine, "USED_ON_REQUEST", "-2", "13", "2026-11-19T09:00:00Z");
        insertMovement(other, "RECEIVED", "1", "11", "2026-11-19T10:00:00Z");
        // A row written before V52 has no type or balance. The check that now forbids that is dropped inside this
        // test's transaction only (DDL is transactional in Postgres), so the row can stand in for an old one.
        jdbc.execute("ALTER TABLE stock_movements DROP CONSTRAINT ck_stock_movements_recorded");
        jdbc.update("INSERT INTO stock_movements (id, stock_item_id, delta, created_by, created_at) "
                + "VALUES (?, ?, -1, 'legacy@test.local', '2026-11-19T11:00:00Z')", UUID.randomUUID(), mine);

        String body = mockMvc.perform(get(PATH + "/movements").with(EquipmentFixtures.as("REVIEWER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> rows = JsonPath.read(body, "$.items[?(@.itemName == 'History item')]");
        assertEquals(2, rows.size());
        assertEquals("USED_ON_REQUEST", rows.get(0).get("type"));
        assertEquals(2.0, ((Number) rows.get(0).get("quantity")).doubleValue());
        assertEquals(13.0, ((Number) rows.get(0).get("balance")).doubleValue());
        assertEquals("RECEIVED", rows.get(1).get("type"));
        List<Object> foreign = JsonPath.read(body, "$.items[?(@.itemName == 'Other history item')]");
        assertTrue(foreign.isEmpty());
    }

    // ---- record ----------------------------------------------------------------------------------------------

    @Test
    void receivingStockRaisesTheBalanceAndTheAuditLogHoldsNoText() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID item = fixtures.stockItem("TELECOMS", "ESP32 boards", "pcs", "18", "25");
        String actor = fixtures.coordinatorEmail("Telecoms");

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as(actor, "DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(item, "RECEIVED", "20", "Delivery from Kampala supplier", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.id").isString())
                .andExpect(jsonPath("$.movement.itemId").value(item.toString()))
                .andExpect(jsonPath("$.movement.itemName").value("ESP32 boards"))
                .andExpect(jsonPath("$.movement.type").value("RECEIVED"))
                .andExpect(jsonPath("$.movement.quantity").value(20.0))
                .andExpect(jsonPath("$.movement.balance").value(38.0))
                .andExpect(jsonPath("$.movement.note").value("Delivery from Kampala supplier"))
                .andExpect(jsonPath("$.movement.requestId").value((Object) null))
                .andExpect(jsonPath("$.movement.by").value(actor))
                .andExpect(jsonPath("$.movement.at").isString())
                .andExpect(jsonPath("$.item.quantity").value(38.0))
                .andExpect(jsonPath("$.item.name").value("ESP32 boards"));

        entityManager.flush();
        List<Map<String, Object>> entries = fixtures.auditEntries(actor, "stock.movement_recorded");
        assertEquals(1, entries.size());
        String all = entries.get(0).get("before_text") + " " + entries.get(0).get("after_text");
        assertTrue(all.contains("RECEIVED") && all.contains("38"), all);
        assertFalse(all.contains("Kampala") || all.contains("ESP32"), all);
    }

    @Test
    void usingStockOnARequestKeepsTheRequestIdAndAcceptsFractions() throws Exception {
        UUID item = fixtures().stockItem("TELECOMS", "Solder wire", "spools", "8", "5");

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(item, "USED_ON_REQUEST", "1.5", null, "REQ-2026-014")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.quantity").value(1.5))
                .andExpect(jsonPath("$.movement.balance").value(6.5))
                .andExpect(jsonPath("$.movement.requestId").value("REQ-2026-014"))
                .andExpect(jsonPath("$.item.quantity").value(6.5));
    }

    @Test
    void usingMoreThanIsInStockIsRefusedAndRecordsNothing() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID item = fixtures.stockItem("TELECOMS", "Last connectors", "pcs", "3", "0");

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(item, "USED_ON_REQUEST", "3.01", null, "REQ-1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("below zero")));

        entityManager.flush();
        assertEquals(0, new BigDecimal("3").compareTo(jdbc.queryForObject(
                "SELECT quantity_on_hand FROM stock_items WHERE id = ?", BigDecimal.class, item)));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE stock_item_id = ?", Long.class, item));
    }

    @Test
    void aCorrectionNeedsANoteAndMayBeNegative() throws Exception {
        UUID item = fixtures().stockItem("TELECOMS", "Counted item", "pcs", "10", "0");

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "CORRECTION", "-2", null, null)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "CORRECTION", "-2", "Recount found two fewer", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.quantity").value(-2.0))
                .andExpect(jsonPath("$.movement.balance").value(8.0));
    }

    @Test
    void badMovementBodiesAreValidationErrors() throws Exception {
        UUID item = fixtures().stockItem("TELECOMS", "Validated item", "pcs", "10", "0");

        for (String json : List.of(
                movement(item, "RECEIVED", "0", null, null),
                movement(item, "RECEIVED", "-1", null, null),
                movement(item, "USED_ON_REQUEST", "1", null, null),
                movement(item, "USED_ON_ORDER", "1", null, "ORD-1"),
                movement(item, "RECEIVED", "1.234", null, null),
                "{\"type\": \"RECEIVED\", \"quantity\": 1}",
                "{\"itemId\": \"%s\", \"quantity\": 1}".formatted(item))) {
            mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnprocessableContent());
        }
        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "STOLEN", "1", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    void recordingForAnUnknownOrForeignItemIsNotFound() throws Exception {
        UUID textile = fixtures().stockItem("TEXTILE", "Cotton", "metres", "5", "1");

        for (UUID id : List.of(UUID.randomUUID(), textile)) {
            mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(movement(id, "RECEIVED", "1", null, null)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void onlyManagersOfTheDepartmentRecordMovementsAndChangeMinimums() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID item = fixtures.stockItem("TELECOMS", "Guarded item", "pcs", "10", "1");

        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_MANAGE) {
            mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(movement(item, "RECEIVED", "1", null, null)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch(PATH + "/{id}/minimum", item).with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 3}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH + "/movements").with(fixtures.coordinatorOf("Textile"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "RECEIVED", "1", null, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch(PATH + "/{id}/minimum", item).with(fixtures.coordinatorOf("Textile"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void droppingBelowTheMinimumTellsTheManagersOnceOnly() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String admin = fixtures.insertUser("ADMINISTRATOR");
        UUID item = fixtures.stockItem("TELECOMS", "Watched item", "pcs", "10", "8");

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(movement(item, "USED_ON_REQUEST", "1", null, "R-1")));
        entityManager.flush();
        assertEquals(0, fixtures.notificationsFor(admin, "LOW_STOCK"));

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(movement(item, "USED_ON_REQUEST", "2", null, "R-2")));
        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(movement(item, "USED_ON_REQUEST", "1", null, "R-3")));

        entityManager.flush();
        assertEquals(1, fixtures.notificationsFor(admin, "LOW_STOCK"));
    }

    // ---- minimum ---------------------------------------------------------------------------------------------

    @Test
    void changingTheMinimumReturnsTheItemAndChangesNothingElse() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID item = fixtures.stockItem("TELECOMS", "Min item", "pcs", "10", "5");
        String actor = EquipmentFixtures.email("admin");

        mockMvc.perform(patch(PATH + "/{id}/minimum", item).with(EquipmentFixtures.as(actor, "ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 12.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimum").value(12.5))
                .andExpect(jsonPath("$.quantity").value(10.0))
                .andExpect(jsonPath("$.name").value("Min item"));

        entityManager.flush();
        assertEquals(1, fixtures.auditEntries(actor, "stock.minimum_changed").size());
    }

    @Test
    void theMinimumMustBeSentAndMustNotBeNegative() throws Exception {
        UUID item = fixtures().stockItem("TELECOMS", "Min rules", "pcs", "10", "5");

        for (String json : List.of("{\"minimum\": -1}", "{}", "{\"minimum\": null}")) {
            mockMvc.perform(patch(PATH + "/{id}/minimum", item).with(EquipmentFixtures.as("ADMINISTRATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnprocessableContent());
        }
        mockMvc.perform(patch(PATH + "/{id}/minimum", UUID.randomUUID()).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(PATH + "/{id}/minimum", item).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 1, \"quantity\": 99}"))
                .andExpect(status().isBadRequest());
    }

    // ---- register an item ------------------------------------------------------------------------------------

    @Test
    void registeringAnItemGeneratesASkuAndRecordsTheOpeningBalanceAsAMovement() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String actor = fixtures.coordinatorEmail("Telecoms");

        String body = mockMvc.perform(post(PATH).with(EquipmentFixtures.as(actor, "DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"BNC connectors\", \"unit\": \"pcs\", \"quantity\": 40, \"minimum\": 10, \"location\": \"Cabinet A1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("BNC connectors"))
                .andExpect(jsonPath("$.unit").value("pcs"))
                .andExpect(jsonPath("$.quantity").value(40.0))
                .andExpect(jsonPath("$.minimum").value(10.0))
                .andExpect(jsonPath("$.location").value("Cabinet A1"))
                .andReturn().getResponse().getContentAsString();

        entityManager.flush();
        String id = JsonPath.read(body, "$.id");
        assertTrue(jdbc.queryForObject("SELECT sku FROM stock_items WHERE id = ?::uuid", String.class, id).startsWith("TEL-"));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE stock_item_id = ?::uuid AND movement_type = 'RECEIVED'", Long.class, id));
        assertEquals(1, fixtures.auditEntries(actor, "stock.item_registered").size());
    }

    @Test
    void registeringNeedsAValidBodyAnUnusedSkuAndAManager() throws Exception {
        String sku = "TEL-DUP-" + UUID.randomUUID().toString().substring(0, 6);
        String json = "{\"name\": \"Dup\", \"unit\": \"pcs\", \"sku\": \"%s\"}".formatted(sku);
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated());

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.replace(sku, sku.toLowerCase())))
                .andExpect(status().isConflict());
        for (String bad : List.of("{\"unit\": \"pcs\"}", "{\"name\": \"x\"}", "{\"name\": \"x\", \"unit\": \"pcs\", \"quantity\": -1}")) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isUnprocessableContent());
        }
        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_MANAGE) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void textThePlatformCannotStoreIsAValidationErrorAndAHugeBalanceIsRefused() throws Exception {
        UUID item = fixtures().stockItem("TELECOMS", "Limit item", "pcs", "9999999999", "0");

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Bad\\u0000name\", \"unit\": \"pcs\"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "RECEIVED", "1", "Bad\\u0000note", null)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "RECEIVED", "5", null, null)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void changingTheMinimumAboveTheBalanceAndRegisteringALowItemAlsoTellTheManagers() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String admin = fixtures.insertUser("ADMINISTRATOR");
        UUID item = fixtures.stockItem("TELECOMS", "Raised minimum", "pcs", "10", "5");

        mockMvc.perform(patch(PATH + "/{id}/minimum", item).with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 4}")).andExpect(status().isOk());
        entityManager.flush();
        assertEquals(0, fixtures.notificationsFor(admin, "LOW_STOCK"));

        mockMvc.perform(patch(PATH + "/{id}/minimum", item).with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 12}")).andExpect(status().isOk());
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Starts low\", \"unit\": \"pcs\", \"quantity\": 1, \"minimum\": 5}")).andExpect(status().isCreated());

        entityManager.flush();
        assertEquals(2, fixtures.notificationsFor(admin, "LOW_STOCK"));
    }

    @Test
    void aReferenceOnReceivedStockIsNotKeptAsAnUsageReference() throws Exception {
        UUID item = fixtures().stockItem("TELECOMS", "Reference item", "pcs", "10", "0");

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "RECEIVED", "1", null, "REQ-9")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.requestId").value((Object) null));
    }

    private void insertMovement(UUID item, String type, String delta, String balance, String at) {
        jdbc.update("INSERT INTO stock_movements (id, stock_item_id, delta, movement_type, balance_after, created_by, "
                        + "created_at) VALUES (?, ?, ?::numeric, ?, ?::numeric, 'seed@test.local', ?::timestamptz)",
                UUID.randomUUID(), item, delta, type, balance, at);
    }

    private static String movement(UUID item, String type, String quantity, String note, String requestId) {
        StringBuilder json = new StringBuilder("{\"itemId\": \"%s\", \"type\": \"%s\", \"quantity\": %s".formatted(item, type, quantity));
        if (note != null) {
            json.append(", \"note\": \"").append(note).append('"');
        }
        if (requestId != null) {
            json.append(", \"requestId\": \"").append(requestId).append('"');
        }
        return json.append('}').toString();
    }
}
