package org.unipod.unipoderpbackend.controller.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class TelecomsEquipmentApiIntegrationTest {
    private static final String PATH = "/telecoms/equipment";

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

    @Test
    void listsOnlyTelecomsEquipmentInAnItemsWrapperWithoutACategory() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        fixtures.equipment("TELECOMS", "Spectrum analyser " + suffix, 2, 1, "IN_USE");
        fixtures.equipment("MULTIMEDIA", "Camera " + suffix, 1, 1, "AVAILABLE");

        String body = mockMvc.perform(get(PATH).with(EquipmentFixtures.as("OBSERVER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> rows = JsonPath.read(body, "$.items[?(@.name =~ /.*" + suffix + "/)]");
        assertEquals(1, rows.size());
        assertEquals("Spectrum analyser " + suffix, rows.get(0).get("name"));
        assertEquals("IN_USE", rows.get(0).get("state"));
        assertEquals(2, rows.get(0).get("totalQuantity"));
        assertEquals(1, rows.get(0).get("availableQuantity"));
        assertFalse(rows.get(0).containsKey("category"));
    }

    @Test
    void listIsRefusedForSubmittersAndAnonymousCallers() throws Exception {
        mockMvc.perform(get(PATH).with(EquipmentFixtures.as("SUBMITTER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void aCoordinatorOfTelecomsRegistersEquipmentAndOthersCannot() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String actor = fixtures.coordinatorEmail("Telecoms");

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as(actor, "DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Oscilloscope")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("AVAILABLE"))
                .andExpect(jsonPath("$.category").doesNotExist());

        entityManager.flush();
        assertEquals(1, fixtures.auditEntries(actor, "equipment.registered").size());
        mockMvc.perform(post(PATH).with(fixtures.coordinatorOf("Multimedia"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("Oscilloscope")))
                .andExpect(status().isForbidden());
        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_MANAGE) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(body("Oscilloscope")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void registeringNeedsAValidBodyAndAFreshAssetTag() throws Exception {
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Scope\", \"assetTag\": \"TL-1\", \"totalQuantity\": -1}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String tag = "TL-DUP-" + UUID.randomUUID();
        String json = "{\"name\": \"Scope\", \"assetTag\": \"%s\", \"totalQuantity\": 1}".formatted(tag);
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated());
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }

    @Test
    void reportingAFaultFlagsTheItemAndKeepsTheDescriptionOutOfTheAuditLog() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID id = fixtures.equipment("TELECOMS", "Waveform generator", 1, 1, "AVAILABLE");
        String actor = EquipmentFixtures.email("technician");

        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as(actor, "FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\": \"%s\", \"description\": \"Output channel two is dead\"}".formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAULTY"))
                .andExpect(jsonPath("$.note").value("Output channel two is dead"))
                .andExpect(jsonPath("$.category").doesNotExist());

        entityManager.flush();
        List<Map<String, Object>> entries = fixtures.auditEntries(actor, "equipment.fault_reported");
        assertEquals(1, entries.size());
        String all = entries.get(0).get("before_text") + " " + entries.get(0).get("after_text");
        assertTrue(all.contains("FAULTY"), all);
        assertFalse(all.contains("channel") || all.contains("Waveform"), all);
    }

    @Test
    void faultReportsFollowTheRolesAndTheDepartment() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID id = fixtures.equipment("TELECOMS", "Logic analyser", 1, 1, "AVAILABLE");
        UUID multimedia = fixtures.equipment("MULTIMEDIA", "Camera", 1, 1, "AVAILABLE");
        String json = "{\"equipmentId\": \"%s\", \"description\": \"Probe broken\"}";

        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_REPORT) {
            mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content(json.formatted(id)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH + "/report-fault").with(fixtures.coordinatorOf("Textile"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.formatted(id)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.formatted(multimedia)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/report-fault").with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"equipmentId\": \"%s\", \"description\": \"\"}".formatted(id)))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(post(PATH + "/report-fault").with(fixtures.coordinatorOf("Telecoms"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.formatted(id)))
                .andExpect(status().isOk());
    }

    // ---- mark working, and text the database cannot store --------------------------------------------------------

    @Test
    void markingFaultyEquipmentWorkingClearsTheFaultAndFollowsTheRules() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID faulty = fixtures.equipment("TELECOMS", "Repaired item", 2, 2, "FAULTY");
        UUID healthy = fixtures.equipment("TELECOMS", "Healthy item", 2, 2, "AVAILABLE");
        UUID foreign = fixtures.equipment("MULTIMEDIA", "Other department item", 2, 2, "FAULTY");
        String actor = fixtures.coordinatorEmail("Telecoms");

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
        UUID id = fixtures().equipment("TELECOMS", "Nul item", 1, 1, "AVAILABLE");

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

    private static String body(String name) {
        return "{\"name\": \"%s\", \"assetTag\": \"TL-%s\", \"totalQuantity\": 2, \"location\": \"Bench 3\"}"
                .formatted(name, UUID.randomUUID());
    }
}
