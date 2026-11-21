package org.unipod.unipoderpbackend.controller.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class CncStockApiIntegrationTest {
    private static final String PATH = "/cnc/stock";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    private EquipmentFixtures fixtures() {
        return new EquipmentFixtures(jdbc);
    }

    @Test
    void listsCncItemsWithTheWeeksMovementCount() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID plywood = fixtures.stockItem("CNC", "Plywood " + suffix, "sheets", "86", "30");
        fixtures.stockItem("TELECOMS", "Capacitors " + suffix, "pcs", "5", "1");
        long before = movementsThisWeek();
        insertMovement(plywood, Instant.now(clock).minus(2, ChronoUnit.DAYS));
        insertMovement(plywood, Instant.now(clock).minus(10, ChronoUnit.DAYS));

        String body = mockMvc.perform(get(PATH).with(EquipmentFixtures.as("OBSERVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementsThisWeek").value(before + 1))
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> rows = JsonPath.read(body, "$.items[?(@.name =~ /.*" + suffix + "/)]");
        assertEquals(1, rows.size());
        assertEquals("sheets", rows.get(0).get("unit"));
        assertEquals(86.0, ((Number) rows.get(0).get("quantity")).doubleValue());
    }

    @Test
    void readingIsRefusedForSubmittersAndAnonymousCallers() throws Exception {
        for (String path : List.of(PATH, PATH + "/movements")) {
            mockMvc.perform(get(path).with(EquipmentFixtures.as("SUBMITTER"))).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void usingStockOnAnUnknownJobKeepsTheReferenceAsTypedWithoutACode() throws Exception {
        UUID item = fixtures().stockItem("CNC", "Plywood", "sheets", "14", "20");

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(item, "USED_ON_ORDER", "2", null, "CNC-JOB-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.type").value("USED_ON_ORDER"))
                .andExpect(jsonPath("$.movement.quantity").value(2.0))
                .andExpect(jsonPath("$.movement.balanceAfter").value(12.0))
                .andExpect(jsonPath("$.movement.jobId").value("CNC-JOB-1"))
                .andExpect(jsonPath("$.movement.jobCode").value((Object) null))
                .andExpect(jsonPath("$.movement.note").value(""))
                .andExpect(jsonPath("$.item.quantity").value(12.0));
    }

    @Test
    void usingStockOnARealCncJobShowsItsCodeAndLinksIt() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID item = fixtures.stockItem("CNC", "Hardboard", "sheets", "42", "20");
        UUID job = UUID.randomUUID();
        String code = "ORD-CNC-" + UUID.randomUUID().toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO department_orders (id, code, department, customer_id, title, quantity, stage, created_by, "
                        + "created_at, updated_at) VALUES (?, ?, 'CNC', 'customer@test.local', 'Cutting board', 50, 'NEW', "
                        + "'staff@test.local', ?, ?)", job, code, now, now);

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(item, "USED_ON_ORDER", "18", "Cut for board", job.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.jobId").value(job.toString()))
                .andExpect(jsonPath("$.movement.jobCode").value(code))
                .andExpect(jsonPath("$.movement.note").value("Cut for board"));

        entityManager.flush();
        assertEquals(job, jdbc.queryForObject(
                "SELECT department_order_id FROM stock_movements WHERE stock_item_id = ?", UUID.class, item));
    }

    @Test
    void cncDoesNotRecordUsageOnTelecomsRequests() throws Exception {
        UUID item = fixtures().stockItem("CNC", "Blades", "pcs", "220", "100");

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(item, "USED_ON_REQUEST", "1", null, "REQ-1")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void usageNeedsTheJobAndNeverTakesStockBelowZero() throws Exception {
        UUID item = fixtures().stockItem("CNC", "Zips", "pcs", "9", "15");

        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "USED_ON_ORDER", "1", null, null)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "USED_ON_ORDER", "10", null, "CNC-1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void receivingAndCorrectingWorkAndFollowTheRoles() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID item = fixtures.stockItem("CNC", "Pine", "metres", "24", "10");

        mockMvc.perform(post(PATH + "/movements").with(fixtures.coordinatorOf("Cnc"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "RECEIVED", "6", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.balanceAfter").value(30.0));
        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(item, "CORRECTION", "-3", "Damp, discarded", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movement.balanceAfter").value(27.0));
        mockMvc.perform(post(PATH + "/movements").with(fixtures.coordinatorOf("Telecoms"))
                        .contentType(MediaType.APPLICATION_JSON).content(movement(item, "RECEIVED", "1", null, null)))
                .andExpect(status().isForbidden());
        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_MANAGE) {
            mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(movement(item, "RECEIVED", "1", null, null)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(movement(UUID.randomUUID(), "RECEIVED", "1", null, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyShowsTheCncMovementShape() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID item = fixtures.stockItem("CNC", "History plywood", "sheets", "10", "1");
        mockMvc.perform(post(PATH + "/movements").with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(movement(item, "RECEIVED", "5", "Delivery", null)));
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(PATH + "/movements").with(EquipmentFixtures.as("REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.itemName == 'History plywood')].balanceAfter")
                        .value(org.hamcrest.Matchers.contains(15.0)))
                .andExpect(jsonPath("$.items[?(@.itemName == 'History plywood')].jobId")
                        .value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void thePatchChangesOnlyTheMinimum() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID item = fixtures.stockItem("CNC", "Min plywood", "sheets", "50", "30");

        mockMvc.perform(patch(PATH + "/{id}", item).with(fixtures.coordinatorOf("Cnc"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimum").value(40.0))
                .andExpect(jsonPath("$.quantity").value(50.0));
        mockMvc.perform(patch(PATH + "/{id}", item).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": -1}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(patch(PATH + "/{id}", UUID.randomUUID()).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(PATH + "/{id}", item).with(fixtures.coordinatorOf("Telecoms"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registeringACncItemGeneratesACncSku() throws Exception {
        String body = mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Pine board\", \"unit\": \"sheets\", \"minimum\": 12}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(0.0))
                .andExpect(jsonPath("$.minimum").value(12.0))
                .andReturn().getResponse().getContentAsString();

        entityManager.flush();
        String id = JsonPath.read(body, "$.id");
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE stock_item_id = ?::uuid", Long.class, id));
        assertTrue(jdbc.queryForObject("SELECT sku FROM stock_items WHERE id = ?::uuid", String.class, id).startsWith("CNC-"));
    }

    private long movementsThisWeek() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM stock_movements m JOIN stock_items i ON i.id = m.stock_item_id "
                + "WHERE i.department = 'CNC' AND m.movement_type IS NOT NULL AND m.created_at >= now() - interval '7 days'",
                Long.class);
        return count == null ? 0 : count;
    }

    private void insertMovement(UUID item, Instant at) {
        jdbc.update("INSERT INTO stock_movements (id, stock_item_id, delta, movement_type, balance_after, created_by, "
                        + "created_at) VALUES (?, ?, 1, 'RECEIVED', 87, 'seed@test.local', ?)", UUID.randomUUID(), item,
                Timestamp.from(at));
    }

    private static String movement(UUID item, String type, String quantity, String note, String jobId) {
        StringBuilder json = new StringBuilder("{\"itemId\": \"%s\", \"type\": \"%s\", \"quantity\": %s".formatted(item, type, quantity));
        if (note != null) {
            json.append(", \"note\": \"").append(note).append('"');
        }
        if (jobId != null) {
            json.append(", \"jobId\": \"").append(jobId).append('"');
        }
        return json.append('}').toString();
    }
}
