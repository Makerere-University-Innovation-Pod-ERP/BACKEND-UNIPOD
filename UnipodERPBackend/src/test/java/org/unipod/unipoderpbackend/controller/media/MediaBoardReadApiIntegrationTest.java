package org.unipod.unipoderpbackend.controller.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.support.OperationsTestData;
import org.unipod.unipoderpbackend.support.ZodShape;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MediaBoardReadApiIntegrationTest {
    // The frontend's mediaRequestSchema (services/media.js).
    static final ZodShape REQUEST_SHAPE = ZodShape.object()
            .string("id").string("title").nullableString("studio").string("assignee")
            .enumOf("status", "REQUESTED", "SCHEDULED", "IN_PRODUCTION", "DELIVERED")
            .optionalString("date").optionalString("description");
    static final ZodShape OVERVIEW_SHAPE = ZodShape.object()
            .number("requestedCount").number("scheduledCount").number("inProductionCount").number("deliveredCount")
            .arrayOf("requests", REQUEST_SHAPE);

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsOpenRequestsNewestActivityFirstWithTheFrontendFields() throws Exception {
        UUID studio = OperationsTestData.facility(jdbcTemplate, "Multimedia", 6);
        String studioName = jdbcTemplate.queryForObject("SELECT name FROM facilities WHERE id = ?", String.class, studio);
        UUID older = MediaBoardTestData.request(jdbcTemplate, "Older", "REQUESTED", NOW.plusSeconds(10));
        UUID newer = MediaBoardTestData.request(jdbcTemplate, "Newer", "SCHEDULED", NOW.plusSeconds(500),
                studio, "Katia Birungi", Instant.parse("2026-11-20T08:00:00Z"), "Product photography");

        JsonNode list = list();

        int newerIndex = indexOf(list, newer);
        int olderIndex = indexOf(list, older);
        assertTrue(newerIndex < olderIndex, "the request changed last comes first");
        JsonNode newerJson = list.get(newerIndex);
        assertEquals("Newer", newerJson.get("title").asString());
        assertEquals(studioName, newerJson.get("studio").asString());
        assertEquals("Katia Birungi", newerJson.get("assignee").asString());
        assertEquals("SCHEDULED", newerJson.get("status").asString());
        assertEquals("Product photography", newerJson.get("description").asString());
        assertEquals(Instant.parse("2026-11-20T08:00:00Z"),
                java.time.OffsetDateTime.parse(newerJson.get("date").asString()).toInstant());
    }

    @Test
    void anUnassignedRequestHasAnEmptyAssigneeANullStudioAndNoDateOrDescription() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Bare", "REQUESTED", NOW);

        JsonNode card = list().get(indexOf(list(), id));

        REQUEST_SHAPE.check(card);
        assertEquals("", card.get("assignee").asString());
        assertTrue(card.get("studio").isNull());
        assertFalse(card.has("date"));
        assertFalse(card.has("description"));
    }

    @Test
    void showsAClosedRequestAsDeliveredAndLeavesACancelledOneOut() throws Exception {
        UUID closed = MediaBoardTestData.request(jdbcTemplate, "Closed", "COMPLETED", NOW);
        UUID cancelled = MediaBoardTestData.request(jdbcTemplate, "Cancelled", "CANCELLED", NOW);

        JsonNode list = list();

        assertEquals("DELIVERED", list.get(indexOf(list, closed)).get("status").asString());
        assertEquals(-1, indexOf(list, cancelled));
    }

    @Test
    void everyCardMatchesTheFrontendSchema() throws Exception {
        MediaBoardTestData.request(jdbcTemplate, "One", "REQUESTED", NOW);
        MediaBoardTestData.request(jdbcTemplate, "Two", "IN_PRODUCTION", NOW,
                null, "Daniel Kato", NOW, "Docuseries");
        MediaBoardTestData.request(jdbcTemplate, "Three", "DELIVERED", NOW);

        REQUEST_SHAPE.checkAll(list());
    }

    @Test
    void overviewCountsAgreeWithTheListAndCountAClosedRequestAsDelivered() throws Exception {
        JsonNode before = overview();
        MediaBoardTestData.request(jdbcTemplate, "R1", "REQUESTED", NOW);
        MediaBoardTestData.request(jdbcTemplate, "R2", "REQUESTED", NOW);
        MediaBoardTestData.request(jdbcTemplate, "S1", "SCHEDULED", NOW);
        MediaBoardTestData.request(jdbcTemplate, "P1", "IN_PRODUCTION", NOW);
        MediaBoardTestData.request(jdbcTemplate, "D1", "DELIVERED", NOW);
        MediaBoardTestData.request(jdbcTemplate, "D2", "COMPLETED", NOW);
        MediaBoardTestData.request(jdbcTemplate, "X1", "CANCELLED", NOW);

        JsonNode after = overview();

        OVERVIEW_SHAPE.check(after);
        assertEquals(before.get("requestedCount").asLong() + 2, after.get("requestedCount").asLong());
        assertEquals(before.get("scheduledCount").asLong() + 1, after.get("scheduledCount").asLong());
        assertEquals(before.get("inProductionCount").asLong() + 1, after.get("inProductionCount").asLong());
        assertEquals(before.get("deliveredCount").asLong() + 2, after.get("deliveredCount").asLong());
        for (String column : new String[] {"REQUESTED", "SCHEDULED", "IN_PRODUCTION", "DELIVERED"}) {
            long inList = 0;
            for (JsonNode card : after.get("requests")) {
                if (column.equals(card.get("status").asString())) {
                    inList++;
                }
            }
            String key = switch (column) {
                case "REQUESTED" -> "requestedCount";
                case "SCHEDULED" -> "scheduledCount";
                case "IN_PRODUCTION" -> "inProductionCount";
                default -> "deliveredCount";
            };
            assertEquals(inList, after.get(key).asLong(), key + " must equal the cards in that column");
        }
        assertEquals(list().size(), after.get("requests").size());
    }

    @Test
    void everyRoleExceptSubmitterMayReadTheBoard() throws Exception {
        for (String role : MediaBoardTestData.READ_ROLES) {
            mockMvc.perform(get("/operations/media/requests").with(as(role))).andExpect(status().isOk());
            mockMvc.perform(get("/operations/media/overview").with(as(role))).andExpect(status().isOk());
        }
    }

    @Test
    void aSubmitterIsRefusedAndAnAnonymousCallerIsNotSignedIn() throws Exception {
        mockMvc.perform(get("/operations/media/requests").with(as("SUBMITTER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/operations/media/overview").with(as("SUBMITTER"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/operations/media/requests")).andExpect(status().isUnauthorized());
    }

    private JsonNode list() throws Exception {
        return ZodShape.parse(mockMvc.perform(get("/operations/media/requests").with(as("OBSERVER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode overview() throws Exception {
        return ZodShape.parse(mockMvc.perform(get("/operations/media/overview").with(as("OBSERVER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static int indexOf(JsonNode list, UUID id) {
        for (int index = 0; index < list.size(); index++) {
            if (id.toString().equals(list.get(index).get("id").asString())) {
                return index;
            }
        }
        return -1;
    }

    private static RequestPostProcessor as(String role) {
        return user("board-" + UUID.randomUUID() + "@test.local").roles(role);
    }
}
