package org.unipod.unipoderpbackend.controller.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every response is checked field by field against the zod schema the frontend parses it with
 * (`src/services/equipment.js`, `telecoms.js`, `textile.js` in the frontend repository). A schema field the API
 * does not send, or an extra one, fails here. Types: `s` string, `s?` string or null, `n` number, `a` array,
 * `o` object, `o?` object or null.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FrontendContractTest {
    // services/equipment.js equipmentItemSchema
    private static final Map<String, String> MULTIMEDIA_EQUIPMENT = shape("id:s", "name:s", "category:s", "assetTag:s",
            "totalQuantity:n", "availableQuantity:n", "state:s", "location:s", "note:s", "updatedAt:s");
    // services/telecoms.js equipmentItemSchema
    private static final Map<String, String> TELECOMS_EQUIPMENT = shape("id:s", "name:s", "assetTag:s",
            "totalQuantity:n", "availableQuantity:n", "state:s", "location:s", "note:s", "updatedAt:s");
    // services/telecoms.js equipmentLoanSchema
    private static final Map<String, String> LOAN = shape("id:s", "equipmentId:s", "equipmentName:s", "quantity:n",
            "borrowerName:s", "borrowerType:s", "borrowerContact:s", "dateOut:s", "dateBack:s", "outgoingCondition:s",
            "incomingCondition:s?", "status:s", "managerApproval:o?", "createdAt:s");
    private static final Map<String, String> MANAGER_APPROVAL = shape("approvedBy:s", "approvedAt:s", "note:s?");
    // services/telecoms.js and textile.js stockItemSchema (identical)
    private static final Map<String, String> STOCK_ITEM = shape("id:s", "name:s", "unit:s", "quantity:n", "minimum:n",
            "location:s");
    // services/telecoms.js stockMovementSchema
    private static final Map<String, String> TELECOMS_MOVEMENT = shape("id:s", "itemId:s", "itemName:s", "type:s",
            "quantity:n", "balance:n", "note:s?", "requestId:s?", "by:s", "at:s");
    // services/textile.js stockMovementSchema
    private static final Map<String, String> TEXTILE_MOVEMENT = shape("id:s", "itemId:s", "itemName:s", "type:s",
            "quantity:n", "balanceAfter:n", "orderId:s?", "orderCode:s?", "note:s", "by:s", "at:s");
    // services/textile.js machineSchema and faultReportResultSchema
    private static final Map<String, String> MACHINE = shape("id:s", "name:s", "state:s", "note:s", "updatedAt:s");
    private static final Map<String, String> MACHINE_REPORT_RESULT = shape("machine:o", "report:o", "managersNotified:a");
    private static final Map<String, String> MACHINE_REPORT = shape("id:s", "kind:s", "description:s", "photo:o?",
            "by:s", "at:s");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private final RequestPostProcessor admin = EquipmentFixtures.as("ADMINISTRATOR");

    @Test
    void multimediaEquipmentMatchesTheFrontendSchema() throws Exception {
        UUID id = fixtures().equipment("MULTIMEDIA", "Contract camera", 2, 2, "AVAILABLE");

        String list = body(MockMvcRequestBuilders.get("/operations/equipment").with(admin), 200);
        assertShape(list, "$[?(@.id == '" + id + "')]", MULTIMEDIA_EQUIPMENT, true);
        String registered = body(MockMvcRequestBuilders.post("/operations/equipment").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Contract mic\", \"category\": \"Audio\", \"assetTag\": \"CT-" + UUID.randomUUID()
                        + "\", \"totalQuantity\": 1}"), 201);
        assertShape(registered, "$", MULTIMEDIA_EQUIPMENT, false);
        String faulted = body(MockMvcRequestBuilders.post("/operations/equipment/report-fault").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentId\": \"" + id + "\", \"description\": \"Broken\"}"), 200);
        assertShape(faulted, "$", MULTIMEDIA_EQUIPMENT, false);
        assertShape(body(MockMvcRequestBuilders.post("/operations/equipment/{id}/mark-working", id).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"Fixed\"}"), 200), "$", MULTIMEDIA_EQUIPMENT, false);
    }

    @Test
    void telecomsEquipmentMatchesTheFrontendSchema() throws Exception {
        UUID id = fixtures().equipment("TELECOMS", "Contract scope", 2, 2, "AVAILABLE");

        String list = body(MockMvcRequestBuilders.get("/telecoms/equipment").with(admin), 200);
        assertEquals(Set.of("items"), keys(list, "$"));
        assertShape(list, "$.items[?(@.id == '" + id + "')]", TELECOMS_EQUIPMENT, true);
        String registered = body(MockMvcRequestBuilders.post("/telecoms/equipment").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Contract probe\", \"assetTag\": \"CT-" + UUID.randomUUID() + "\", \"totalQuantity\": 1}"), 201);
        assertShape(registered, "$", TELECOMS_EQUIPMENT, false);
        String faulted = body(MockMvcRequestBuilders.post("/telecoms/equipment/report-fault").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentId\": \"" + id + "\", \"description\": \"Broken\"}"), 200);
        assertShape(faulted, "$", TELECOMS_EQUIPMENT, false);
        assertShape(body(MockMvcRequestBuilders.post("/telecoms/equipment/{id}/mark-working", id).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"Fixed\"}"), 200), "$", TELECOMS_EQUIPMENT, false);
    }

    @Test
    void loansMatchTheFrontendSchemaBeforeAndAfterTheDecision() throws Exception {
        UUID equipment = fixtures().equipment("TELECOMS", "Contract loan item", 3, 3, "AVAILABLE");
        LocalDate today = LocalDate.now(clock);
        String pending = body(MockMvcRequestBuilders.post("/telecoms/equipment/loans").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentId\": \"%s\", \"quantity\": 1, \"borrowerName\": \"Ann\", \"borrowerType\": \"Staff\", "
                        .formatted(equipment) + "\"borrowerContact\": \"c\", \"dateOut\": \"" + today + "\", \"dateBack\": \""
                        + today.plusDays(3) + "\", \"outgoingCondition\": \"Clean\"}"), 201);
        assertShape(pending, "$", LOAN, false);
        assertEquals(null, JsonPath.<Object>read(pending, "$.managerApproval"));
        assertEquals(null, JsonPath.<Object>read(pending, "$.incomingCondition"));
        String id = JsonPath.read(pending, "$.id");

        String approved = body(MockMvcRequestBuilders.post("/telecoms/equipment/loans/{id}/approve", id).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"ok\"}"), 200);
        assertShape(approved, "$", LOAN, false);
        assertShape(approved, "$.managerApproval", MANAGER_APPROVAL, false);
        String returned = body(MockMvcRequestBuilders.post("/telecoms/equipment/loans/{id}/return", id).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"condition\": \"Fine\"}"), 200);
        assertShape(returned, "$", LOAN, false);
        String second = body(MockMvcRequestBuilders.post("/telecoms/equipment/loans").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentId\": \"%s\", \"borrowerName\": \"Bo\", \"borrowerContact\": \"c\", ".formatted(equipment)
                        + "\"dateOut\": \"" + today + "\", \"dateBack\": \"" + today + "\"}"), 201);
        String declined = body(MockMvcRequestBuilders.post("/telecoms/equipment/loans/{id}/decline",
                JsonPath.<String>read(second, "$.id")).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"No\"}"), 200);
        assertShape(declined, "$.managerApproval", MANAGER_APPROVAL, false);

        String list = body(MockMvcRequestBuilders.get("/telecoms/equipment/loans").with(admin), 200);
        assertEquals(Set.of("items"), keys(list, "$"));
        assertShape(list, "$.items[?(@.id == '" + id + "')]", LOAN, true);
    }

    @Test
    void machinesMatchTheFrontendSchema() throws Exception {
        UUID id = fixtures().machine("TEXTILE", "Contract machine", "WORKING");

        String list = body(MockMvcRequestBuilders.get("/textile/machines").with(admin), 200);
        assertEquals(Set.of("items"), keys(list, "$"));
        assertShape(list, "$.items[?(@.id == '" + id + "')]", MACHINE, true);
        assertShape(body(MockMvcRequestBuilders.post("/textile/machines").with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Contract loom " + UUID.randomUUID() + "\"}"), 201),
                "$", MACHINE, false);

        String withPhoto = body(MockMvcRequestBuilders.multipart("/textile/machines/{id}/reports", id).with(admin)
                .file(new MockMultipartFile("report", "", MediaType.APPLICATION_JSON_VALUE,
                        "{\"kind\": \"FAULT\", \"description\": \"Noise\"}".getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("photo", "noise.png", "image/png", new byte[] {1})), 201);
        assertEquals(new TreeSet<>(MACHINE_REPORT_RESULT.keySet()), keys(withPhoto, "$"));
        assertShape(withPhoto, "$.machine", MACHINE, false);
        assertShape(withPhoto, "$.report", MACHINE_REPORT, false);
        assertEquals(Set.of("name"), keys(withPhoto, "$.report.photo"));
        assertTrue(JsonPath.<List<Object>>read(withPhoto, "$.managersNotified") != null);

        String withoutPhoto = body(MockMvcRequestBuilders.multipart("/textile/machines/{id}/reports", id).with(admin)
                .file(new MockMultipartFile("report", "", MediaType.APPLICATION_JSON_VALUE,
                        "{\"kind\": \"SERVICE\", \"description\": \"Service due\"}".getBytes(StandardCharsets.UTF_8))), 201);
        assertEquals(null, JsonPath.<Object>read(withoutPhoto, "$.report.photo"));

        assertShape(body(MockMvcRequestBuilders.post("/textile/machines/{id}/mark-working", id).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"Fixed\"}"), 200), "$", MACHINE, false);
    }

    @Test
    void telecomsStockMatchesTheFrontendSchema() throws Exception {
        UUID item = fixtures().stockItem("TELECOMS", "Contract resistors", "pcs", "10", "2");

        String list = body(MockMvcRequestBuilders.get("/telecoms/stock").with(admin), 200);
        assertEquals(Set.of("items"), keys(list, "$"));
        assertShape(list, "$.items[?(@.id == '" + item + "')]", STOCK_ITEM, true);
        String recorded = body(MockMvcRequestBuilders.post("/telecoms/stock/movements").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": \"" + item + "\", \"type\": \"USED_ON_REQUEST\", \"quantity\": 1.5, \"requestId\": \"R-1\"}"), 201);
        assertEquals(Set.of("item", "movement"), keys(recorded, "$"));
        assertShape(recorded, "$.movement", TELECOMS_MOVEMENT, false);
        assertShape(recorded, "$.item", STOCK_ITEM, false);
        String history = body(MockMvcRequestBuilders.get("/telecoms/stock/movements").with(admin), 200);
        assertEquals(Set.of("items"), keys(history, "$"));
        assertShape(history, "$.items[?(@.itemId == '" + item + "')]", TELECOMS_MOVEMENT, true);
        assertShape(body(MockMvcRequestBuilders.patch("/telecoms/stock/{id}/minimum", item).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 4}"), 200), "$", STOCK_ITEM, false);
    }

    @Test
    void textileStockMatchesTheFrontendSchema() throws Exception {
        UUID item = fixtures().stockItem("TEXTILE", "Contract cotton", "metres", "10", "2");

        String list = body(MockMvcRequestBuilders.get("/textile/stock").with(admin), 200);
        assertEquals(Set.of("items", "movementsThisWeek"), keys(list, "$"));
        assertTrue(JsonPath.<Object>read(list, "$.movementsThisWeek") instanceof Number);
        assertShape(list, "$.items[?(@.id == '" + item + "')]", STOCK_ITEM, true);
        String recorded = body(MockMvcRequestBuilders.post("/textile/stock/movements").with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\": \"" + item + "\", \"type\": \"USED_ON_ORDER\", \"quantity\": 2, \"orderId\": \"TXO-1\"}"), 201);
        assertEquals(Set.of("item", "movement"), keys(recorded, "$"));
        assertShape(recorded, "$.movement", TEXTILE_MOVEMENT, false);
        assertShape(recorded, "$.item", STOCK_ITEM, false);
        String history = body(MockMvcRequestBuilders.get("/textile/stock/movements").with(admin), 200);
        assertShape(history, "$.items[?(@.itemId == '" + item + "')]", TEXTILE_MOVEMENT, true);
        assertShape(body(MockMvcRequestBuilders.patch("/textile/stock/{id}", item).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"minimum\": 4}"), 200), "$", STOCK_ITEM, false);
    }

    private EquipmentFixtures fixtures() {
        return new EquipmentFixtures(jdbc);
    }

    private String body(RequestBuilder request, int expectedStatus) throws Exception {
        return mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private static Map<String, String> shape(String... fields) {
        Map<String, String> shape = new TreeMap<>();
        for (String field : fields) {
            shape.put(field.substring(0, field.indexOf(':')), field.substring(field.indexOf(':') + 1));
        }
        return shape;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> keys(String json, String path) {
        Object found = JsonPath.read(json, path);
        Map<String, Object> object = found instanceof List<?> list ? (Map<String, Object>) list.get(0) : (Map<String, Object>) found;
        return new TreeSet<>(object.keySet());
    }

    /** The object at {@code path} has exactly the schema's fields, and each has the schema's type. */
    @SuppressWarnings("unchecked")
    private static void assertShape(String json, String path, Map<String, String> expected, boolean filtered) {
        Object found = JsonPath.read(json, path);
        Map<String, Object> object;
        if (filtered) {
            List<Map<String, Object>> matches = (List<Map<String, Object>>) found;
            assertEquals(1, matches.size(), path);
            object = matches.get(0);
        } else {
            object = (Map<String, Object>) found;
        }
        assertEquals(new TreeSet<>(expected.keySet()), new TreeSet<>(object.keySet()), path);
        expected.forEach((field, type) -> {
            Object value = object.get(field);
            String where = path + "." + field + " = " + value;
            switch (type) {
                case "s" -> assertTrue(value instanceof String, where);
                case "s?" -> assertTrue(value == null || value instanceof String, where);
                case "n" -> assertTrue(value instanceof Number, where);
                case "a" -> assertTrue(value instanceof List, where);
                case "o" -> assertTrue(value instanceof Map, where);
                case "o?" -> assertTrue(value == null || value instanceof Map, where);
                default -> throw new IllegalArgumentException(type);
            }
        });
    }
}
