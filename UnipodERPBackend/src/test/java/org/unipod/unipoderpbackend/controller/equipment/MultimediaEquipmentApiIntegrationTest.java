package org.unipod.unipoderpbackend.controller.equipment;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MultimediaEquipmentApiIntegrationTest {
    private static final String PATH = "/operations/equipment";

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

    // ---- list ------------------------------------------------------------------------------------------------

    @Test
    void listsOnlyMultimediaEquipmentOrderedByNameWithEveryFieldTheScreenReads() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        fixtures.equipment("MULTIMEDIA", "Zoom mixer " + suffix, 1, 1, "AVAILABLE");
        fixtures.equipment("MULTIMEDIA", "camera body " + suffix, 2, 1, "FAULTY");
        fixtures.equipment("TELECOMS", "Oscilloscope " + suffix, 1, 1, "AVAILABLE");

        String body = mockMvc.perform(get(PATH).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].updatedAt", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andReturn().getResponse().getContentAsString();

        List<String> names = com.jayway.jsonpath.JsonPath.read(body, "$[?(@.name =~ /.*" + suffix + "/)].name");
        assertEquals(List.of("camera body " + suffix, "Zoom mixer " + suffix), names);
        List<Map<String, Object>> camera = com.jayway.jsonpath.JsonPath.read(body, "$[?(@.name == 'camera body " + suffix + "')]");
        assertEquals("FAULTY", camera.get(0).get("state"));
        assertEquals(2, camera.get(0).get("totalQuantity"));
        assertEquals(1, camera.get(0).get("availableQuantity"));
        assertEquals("Test", camera.get(0).get("category"));
        assertEquals("Bench", camera.get(0).get("location"));
        assertEquals("", camera.get(0).get("note"));
    }

    @Test
    void everyReadRoleMayList() throws Exception {
        for (String role : List.of("ADMINISTRATOR", "DEPARTMENT_COORDINATOR", "INNOVATION_COORDINATOR", "RECEPTIONIST",
                "FACT_FINDING_OFFICER", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "FINANCE_OFFICER", "OBSERVER")) {
            mockMvc.perform(get(PATH).with(EquipmentFixtures.as(role))).andExpect(status().isOk());
        }
    }

    @Test
    void listIsRefusedForSubmittersAndAnonymousCallers() throws Exception {
        mockMvc.perform(get(PATH).with(EquipmentFixtures.as("SUBMITTER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    // ---- register --------------------------------------------------------------------------------------------

    @Test
    void anAdministratorRegistersEquipmentAndTheAuditLogHoldsNoNames() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String actor = EquipmentFixtures.email("admin");
        String tag = "MM-" + UUID.randomUUID();

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as(actor, "ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Sony FX3", "category": "Camera", "assetTag": "%s", "totalQuantity": 3,
                                 "location": "Studio A", "note": "New this term"}
                                """.formatted(tag)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sony FX3"))
                .andExpect(jsonPath("$.category").value("Camera"))
                .andExpect(jsonPath("$.assetTag").value(tag))
                .andExpect(jsonPath("$.totalQuantity").value(3))
                .andExpect(jsonPath("$.availableQuantity").value(3))
                .andExpect(jsonPath("$.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.location").value("Studio A"))
                .andExpect(jsonPath("$.note").value("New this term"));

        entityManager.flush();
        List<Map<String, Object>> entries = fixtures.auditEntries(actor, "equipment.registered");
        assertEquals(1, entries.size());
        assertEquals("equipment_item", entries.get(0).get("entity_type"));
        String after = (String) entries.get(0).get("after_text");
        assertTrue(after.contains(tag) && after.contains("MULTIMEDIA"), after);
        assertFalse(after.contains("Sony") || after.contains("New this term"), after);
    }

    @Test
    void aCoordinatorOfMultimediaMayRegisterButACoordinatorOfAnotherDepartmentMayNot() throws Exception {
        EquipmentFixtures fixtures = fixtures();

        mockMvc.perform(post(PATH).with(fixtures.coordinatorOf("Multimedia"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Mic " + UUID.randomUUID(), 1)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(PATH).with(fixtures.coordinatorOf("Textile"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Mic " + UUID.randomUUID(), 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCoordinatorWithNoDepartmentCannotRegister() throws Exception {
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Mic " + UUID.randomUUID(), 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void everyRoleThatCannotManageIsRefusedRegistering() throws Exception {
        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_MANAGE) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(body("Mic " + UUID.randomUUID(), 1)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void registeringWithABadBodyIsAValidationError() throws Exception {
        for (String json : List.of(
                "{\"name\": \"\", \"assetTag\": \"T-1\", \"totalQuantity\": 1}",
                "{\"name\": \"Mic\", \"assetTag\": \" \", \"totalQuantity\": 1}",
                "{\"name\": \"Mic\", \"assetTag\": \"T-1\", \"totalQuantity\": 0}",
                "{\"name\": \"Mic\", \"assetTag\": \"T-1\"}",
                "{\"name\": \"%s\", \"assetTag\": \"T-1\", \"totalQuantity\": 1}".formatted("x".repeat(256)))) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void registeringWithAnUnknownFieldIsAMalformedBody() throws Exception {
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mic\", \"assetTag\": \"T-1\", \"totalQuantity\": 1, \"colour\": \"red\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDuplicateAssetTagIsAConflictEvenInAnotherCase() throws Exception {
        String tag = "MM-DUP-" + UUID.randomUUID();
        String json = "{\"name\": \"Mic\", \"assetTag\": \"%s\", \"totalQuantity\": 1}";
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.formatted(tag)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.formatted(tag.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    // ---- report fault ----------------------------------------------------------------------------------------

    @Test
    void reportingAFaultFlagsTheItemKeepsTheReportAndLeavesTheDescriptionOutOfTheAuditLog() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID id = fixtures.equipment("MULTIMEDIA", "Light kit", 2, 2, "AVAILABLE");
        String actor = EquipmentFixtures.email("reporter");

        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as(actor, "ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\": \"%s\", \"description\": \"Ballast flickers badly\"}".formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.state").value("FAULTY"))
                .andExpect(jsonPath("$.note").value("Ballast flickers badly"))
                .andExpect(jsonPath("$.availableQuantity").value(2));

        entityManager.flush();
        Map<String, Object> report = jdbc.queryForMap(
                "SELECT kind, description, reported_by FROM fault_reports WHERE equipment_id = ?", id);
        assertEquals("FAULT", report.get("kind"));
        assertEquals("Ballast flickers badly", report.get("description"));
        assertEquals(actor, report.get("reported_by"));

        List<Map<String, Object>> entries = fixtures.auditEntries(actor, "equipment.fault_reported");
        assertEquals(1, entries.size());
        String all = entries.get(0).get("before_text") + " " + entries.get(0).get("after_text");
        assertTrue(all.contains("AVAILABLE") && all.contains("FAULTY"), all);
        assertFalse(all.contains("Ballast") || all.contains("Light kit"), all);
    }

    @Test
    void everyRoleThatMayReportCanReport() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID id = fixtures.equipment("MULTIMEDIA", "Tripod", 3, 3, "AVAILABLE");

        for (String role : EquipmentFixtures.ROLES_THAT_CAN_REPORT) {
            mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"equipmentId\": \"%s\", \"description\": \"Leg is loose\"}".formatted(id)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void observersAndSubmittersCannotReportAndCoordinatorsStayInTheirDepartment() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID id = fixtures.equipment("MULTIMEDIA", "Gimbal", 1, 1, "AVAILABLE");
        String json = "{\"equipmentId\": \"%s\", \"description\": \"Motor whines\"}".formatted(id);

        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_REPORT) {
            mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH + "/report-fault").with(fixtures.coordinatorOf("Textile"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + "/report-fault").with(fixtures.coordinatorOf("Multimedia"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void reportingOnUnknownEquipmentOrAnotherDepartmentsEquipmentIsNotFound() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID telecoms = fixtures.equipment("TELECOMS", "Spectrum analyser", 1, 1, "AVAILABLE");

        for (UUID id : List.of(UUID.randomUUID(), telecoms)) {
            mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("ADMINISTRATOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"equipmentId\": \"%s\", \"description\": \"Dead\"}".formatted(id)))
                    .andExpect(status().isNotFound());
        }
        assertEquals("AVAILABLE", fixtures.equipmentRow(telecoms).get("state"));
    }

    @Test
    void aFaultReportNeedsADescriptionAndAnId() throws Exception {
        UUID id = fixtures().equipment("MULTIMEDIA", "Dolly", 1, 1, "AVAILABLE");

        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\": \"%s\", \"description\": \"  \"}".formatted(id)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\": \"Broken\"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\": \"not-an-id\", \"description\": \"Broken\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportingAFaultTellsTheAdministratorsAndTheDepartmentsCoordinators() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String admin = fixtures.insertUser("ADMINISTRATOR");
        String coordinator = fixtures.insertUser("DEPARTMENT_COORDINATOR");
        fixtures.addMember(fixtures.sector("Multimedia"), coordinator);
        String otherCoordinator = fixtures.insertUser("DEPARTMENT_COORDINATOR");
        fixtures.addMember(fixtures.sector("Textile"), otherCoordinator);
        UUID id = fixtures.equipment("MULTIMEDIA", "Recorder", 1, 1, "AVAILABLE");

        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("REVIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\": \"%s\", \"description\": \"No power\"}".formatted(id)))
                .andExpect(status().isOk());

        entityManager.flush();
        assertEquals(1, fixtures.notificationsFor(admin, "FAULT_REPORTED"));
        assertEquals(1, fixtures.notificationsFor(coordinator, "FAULT_REPORTED"));
        assertEquals(0, fixtures.notificationsFor(otherCoordinator, "FAULT_REPORTED"));
    }

    @Test
    void theListShowsTheFaultAfterwards() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID id = fixtures.equipment("MULTIMEDIA", "Slider " + UUID.randomUUID(), 1, 1, "AVAILABLE");
        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipmentId\": \"%s\", \"description\": \"Jammed\"}".formatted(id)));

        mockMvc.perform(get(PATH).with(EquipmentFixtures.as("OBSERVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].state".formatted(id)).value(hasSize(1)))
                .andExpect(jsonPath("$[?(@.id == '%s')].note".formatted(id)).value(org.hamcrest.Matchers.contains("Jammed")));
    }

    // ---- mark working, and text the database cannot store --------------------------------------------------------

    @Test
    void markingFaultyEquipmentWorkingClearsTheFaultAndFollowsTheRules() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID faulty = fixtures.equipment("MULTIMEDIA", "Repaired item", 2, 2, "FAULTY");
        UUID healthy = fixtures.equipment("MULTIMEDIA", "Healthy item", 2, 2, "AVAILABLE");
        UUID foreign = fixtures.equipment("TELECOMS", "Other department item", 2, 2, "FAULTY");
        String actor = fixtures.coordinatorEmail("Multimedia");

        mockMvc.perform(post(PATH + "/{id}/mark-working", faulty).with(EquipmentFixtures.as(actor, "DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"Ballast replaced\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.note").value("Ballast replaced"));

        entityManager.flush();
        List<Map<String, Object>> entries = fixtures.auditEntries(actor, "equipment.marked_working");
        assertEquals(1, entries.size());
        assertFalse(String.valueOf(entries.get(0).get("after_text")).contains("Ballast"));
        mockMvc.perform(post(PATH + "/{id}/mark-working", healthy).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isConflict());
        mockMvc.perform(post(PATH + "/{id}/mark-working", foreign).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/{id}/mark-working", UUID.randomUUID()).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isNotFound());
        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_MANAGE) {
            mockMvc.perform(post(PATH + "/{id}/mark-working", faulty).with(EquipmentFixtures.as(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH + "/{id}/mark-working", faulty).with(fixtures.coordinatorOf("Textile")))
                .andExpect(status().isForbidden());
    }

    @Test
    void textThePlatformCannotStoreIsAValidationErrorNotADuplicateOrAServerError() throws Exception {
        UUID id = fixtures().equipment("MULTIMEDIA", "Nul item", 1, 1, "AVAILABLE");

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Mic\", \"assetTag\": \"NUL-1\", \"totalQuantity\": 1, \"note\": \"a\\u0000b\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\": \"%s\", \"description\": \"a\\u0000b\"}".formatted(id)))
                .andExpect(status().isUnprocessableContent());
    }

    private static String body(String name, int total) {
        return "{\"name\": \"%s\", \"assetTag\": \"MM-%s\", \"totalQuantity\": %d}".formatted(name, UUID.randomUUID(), total);
    }
}
