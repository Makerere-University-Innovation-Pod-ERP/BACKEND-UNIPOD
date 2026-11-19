package org.unipod.unipoderpbackend.controller.media;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.support.AuditTestSupport;
import org.unipod.unipoderpbackend.support.OperationsTestData;
import org.unipod.unipoderpbackend.support.ZodShape;
import tools.jackson.databind.JsonNode;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MediaBoardEditApiIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private UUID multimedia;

    @BeforeEach
    void seedDepartment() {
        multimedia = OperationsTestData.sector(jdbcTemplate, "Multimedia");
    }

    // ----- status

    @Test
    void movesARequestForwardOneStepAtATimeAndAuditsEachMove() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Open day", "REQUESTED", NOW);

        for (String next : new String[] {"SCHEDULED", "IN_PRODUCTION", "DELIVERED"}) {
            JsonNode card = ZodShape.parse(mockMvc.perform(moveTo(id, next, admin()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            MediaBoardReadApiIntegrationTest.REQUEST_SHAPE.check(card);
            assertEquals(next, card.get("status").asString());
            assertEquals(id.toString(), card.get("id").asString());
        }

        List<Map<String, Object>> lines = AuditTestSupport.lines(entityManager, jdbcTemplate, id);
        assertEquals(3, lines.size());
        assertEquals("media_request.status_changed", lines.get(0).get("action"));
        assertEquals("media_request", lines.get(0).get("entity_type"));
        assertEquals("ADMINISTRATOR", lines.get(0).get("actor_role"));
        assertEquals("REQUESTED", ZodShape.parse((String) lines.get(0).get("before_value")).get("status").asString());
        assertEquals("SCHEDULED", ZodShape.parse((String) lines.get(0).get("after_value")).get("status").asString());
    }

    @Test
    void deliveringStampsTheDeliveryTimeAndWhoDidIt() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Podcast", "IN_PRODUCTION", NOW);
        String email = "admin-" + UUID.randomUUID() + "@test.local";

        mockMvc.perform(moveTo(id, "DELIVERED", user(email).roles("ADMINISTRATOR"))).andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT delivered_at, delivered_by FROM media_requests WHERE id = ?", id);
        assertNotNull(row.get("delivered_at"));
        assertEquals(email, row.get("delivered_by"));
    }

    @Test
    void theOverviewFollowsAMove() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Move me", "REQUESTED", NOW);
        long before = overview().get("scheduledCount").asLong();

        mockMvc.perform(moveTo(id, "SCHEDULED", admin())).andExpect(status().isOk());

        assertEquals(before + 1, overview().get("scheduledCount").asLong());
    }

    @ParameterizedTest
    @CsvSource({
            "REQUESTED, IN_PRODUCTION",
            "REQUESTED, DELIVERED",
            "REQUESTED, REQUESTED",
            "SCHEDULED, REQUESTED",
            "SCHEDULED, SCHEDULED",
            "IN_PRODUCTION, SCHEDULED",
            "DELIVERED, DELIVERED",
            "DELIVERED, IN_PRODUCTION",
            "COMPLETED, DELIVERED",
            "CANCELLED, SCHEDULED"
    })
    void refusesAnyMoveThatIsNotTheNextStep(String stored, String target) throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Stuck", stored, NOW);

        mockMvc.perform(moveTo(id, target, admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").isNotEmpty());

        assertEquals(stored, jdbcTemplate.queryForObject(
                "SELECT status FROM media_requests WHERE id = ?", String.class, id));
        assertEquals(0, AuditTestSupport.lines(entityManager, jdbcTemplate, id).size());
    }

    @ParameterizedTest
    @CsvSource({"COMPLETED", "CANCELLED", "DONE"})
    void aStatusTheBoardDoesNotKnowIsAMalformedBody(String value) throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Open", "REQUESTED", NOW);

        mockMvc.perform(patch("/operations/media/requests/{id}/status", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + value + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
    }

    @Test
    void aMissingStatusIsAValidationError() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Open", "REQUESTED", NOW);

        mockMvc.perform(patch("/operations/media/requests/{id}/status", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void anUnknownRequestIsNotFound() throws Exception {
        mockMvc.perform(moveTo(UUID.randomUUID(), "SCHEDULED", admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Media request not found."));
    }

    @Test
    void onlyManagersMayMoveACard() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Guarded", "REQUESTED", NOW);

        for (String role : MediaBoardTestData.NON_MANAGE_ROLES) {
            mockMvc.perform(moveTo(id, "SCHEDULED", as(role))).andExpect(status().isForbidden());
        }
        mockMvc.perform(patch("/operations/media/requests/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"SCHEDULED\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals("REQUESTED", jdbcTemplate.queryForObject(
                "SELECT status FROM media_requests WHERE id = ?", String.class, id));
    }

    @Test
    void aCoordinatorOfTheMultimediaDepartmentMayMoveACard() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Mine", "REQUESTED", NOW);
        String email = "mm-" + UUID.randomUUID() + "@test.local";
        OperationsTestData.member(jdbcTemplate, multimedia, email);

        mockMvc.perform(moveTo(id, "SCHEDULED", user(email).roles("DEPARTMENT_COORDINATOR")))
                .andExpect(status().isOk());

        assertEquals("DEPARTMENT_COORDINATOR",
                AuditTestSupport.lines(entityManager, jdbcTemplate, id).get(0).get("actor_role"));
    }

    @Test
    void aCoordinatorOfAnotherDepartmentIsRefused() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Not mine", "REQUESTED", NOW);
        String email = "tx-" + UUID.randomUUID() + "@test.local";
        OperationsTestData.member(jdbcTemplate, OperationsTestData.sector(jdbcTemplate, "Textile"), email);

        mockMvc.perform(moveTo(id, "SCHEDULED", user(email).roles("DEPARTMENT_COORDINATOR")))
                .andExpect(status().isForbidden());

        assertEquals("REQUESTED", jdbcTemplate.queryForObject(
                "SELECT status FROM media_requests WHERE id = ?", String.class, id));
    }

    // ----- details

    @Test
    void setsTheStudioAssigneeDateAndDescription() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Shoot", "REQUESTED", NOW);
        UUID studio = OperationsTestData.facility(jdbcTemplate, "Multimedia", 6);
        String studioName = jdbcTemplate.queryForObject("SELECT name FROM facilities WHERE id = ?", String.class, studio);

        mockMvc.perform(details(id, """
                        {"studioId": "%s", "assignee": "Katia Birungi", "date": "2026-11-20T11:00:00+03:00",
                         "description": "Product photography"}""".formatted(studio), admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studio").value(studioName))
                .andExpect(jsonPath("$.assignee").value("Katia Birungi"))
                .andExpect(jsonPath("$.description").value("Product photography"))
                .andExpect(jsonPath("$.date").value("2026-11-20T11:00:00+03:00"))
                .andExpect(jsonPath("$.status").value("REQUESTED"));

        // The new values are what the board lists too.
        MediaBoardReadApiIntegrationTest.REQUEST_SHAPE.checkAll(
                ZodShape.parse(mockMvc.perform(get("/operations/media/requests").with(admin()))
                        .andReturn().getResponse().getContentAsString()));
    }

    @Test
    void aBlankAssigneeClearsItAndAnOmittedFieldIsLeftAlone() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Shoot", "SCHEDULED", NOW,
                null, "Katia Birungi", NOW, "Keep me");

        mockMvc.perform(details(id, "{\"assignee\": \"  \"}", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value(""))
                .andExpect(jsonPath("$.description").value("Keep me"));

        assertNull(jdbcTemplate.queryForObject("SELECT assignee FROM media_requests WHERE id = ?", String.class, id));
    }

    @Test
    void anUpdateThatChangesNothingWritesNoAuditLine() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Shoot", "SCHEDULED", NOW,
                null, "Katia Birungi", NOW, "Same");

        mockMvc.perform(details(id, "{\"assignee\": \"Katia Birungi\", \"description\": \"Same\"}", admin()))
                .andExpect(status().isOk());
        mockMvc.perform(details(id, "{}", admin())).andExpect(status().isOk());

        assertEquals(0, AuditTestSupport.lines(entityManager, jdbcTemplate, id).size());
    }

    @Test
    void theDetailsAuditLineHoldsFlagsAndIdsNeverTheAssigneeOrTheDescription() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Shoot", "REQUESTED", NOW);

        mockMvc.perform(details(id, "{\"assignee\": \"Katia Birungi\", \"description\": \"Interview with the dean\"}",
                admin())).andExpect(status().isOk());

        List<Map<String, Object>> lines = AuditTestSupport.lines(entityManager, jdbcTemplate, id);
        assertEquals(1, lines.size());
        assertEquals("media_request.details_changed", lines.get(0).get("action"));
        AuditTestSupport.assertNoPersonalData(lines.get(0), "Katia", "Birungi", "dean", "Interview");
        JsonNode after = ZodShape.parse((String) lines.get(0).get("after_value"));
        assertEquals(java.util.Set.of("studioChanged", "assigneeChanged", "dateChanged", "descriptionChanged"),
                new java.util.HashSet<>(java.util.List.copyOf(after.propertyNames())));
        assertEquals(true, after.get("assigneeChanged").asBoolean());
        assertEquals(true, after.get("descriptionChanged").asBoolean());
        assertEquals(false, after.get("studioChanged").asBoolean());
        assertEquals(false, after.get("dateChanged").asBoolean());
    }

    @Test
    void anUnknownStudioIsAValidationProblem() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Shoot", "REQUESTED", NOW);

        mockMvc.perform(details(id, "{\"studioId\": \"" + UUID.randomUUID() + "\"}", admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Choose an existing studio."));
    }

    @Test
    void tooLongTextIsAValidationError() throws Exception {
        UUID id = MediaBoardTestData.request(jdbcTemplate, "Shoot", "REQUESTED", NOW);

        mockMvc.perform(details(id, "{\"assignee\": \"" + "a".repeat(201) + "\"}", admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("assignee"));
    }

    @Test
    void aFinishedRequestCannotHaveItsDetailsChanged() throws Exception {
        for (String stored : new String[] {"DELIVERED", "COMPLETED", "CANCELLED"}) {
            UUID id = MediaBoardTestData.request(jdbcTemplate, "Done " + stored, stored, NOW);

            mockMvc.perform(details(id, "{\"assignee\": \"Katia\"}", admin()))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void detailsOfAnUnknownRequestAreNotFoundAndOnlyManagersMayChangeThem() throws Exception {
        mockMvc.perform(details(UUID.randomUUID(), "{}", admin())).andExpect(status().isNotFound());

        UUID id = MediaBoardTestData.request(jdbcTemplate, "Guarded", "REQUESTED", NOW);
        for (String role : MediaBoardTestData.NON_MANAGE_ROLES) {
            mockMvc.perform(details(id, "{\"assignee\": \"Katia\"}", as(role))).andExpect(status().isForbidden());
        }
    }

    // ----- helpers

    private JsonNode overview() throws Exception {
        return ZodShape.parse(mockMvc.perform(get("/operations/media/overview").with(admin()))
                .andReturn().getResponse().getContentAsString());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder moveTo(
            UUID id, String target, RequestPostProcessor who) {
        return patch("/operations/media/requests/{id}/status", id).with(who)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + target + "\"}");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder details(
            UUID id, String body, RequestPostProcessor who) {
        return patch("/operations/media/requests/{id}/details", id).with(who)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor admin() {
        return as("ADMINISTRATOR");
    }

    private static RequestPostProcessor as(String role) {
        return user("board-" + UUID.randomUUID() + "@test.local").roles(role);
    }
}
