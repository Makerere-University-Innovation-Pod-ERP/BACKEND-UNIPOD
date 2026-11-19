package org.unipod.unipoderpbackend.controller.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TextileMachineApiIntegrationTest {
    private static final String PATH = "/textile/machines";

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

    // ---- list and register -----------------------------------------------------------------------------------

    @Test
    void listsOnlyTextileMachinesInAnItemsWrapper() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID mine = fixtures.machine("TEXTILE", "Overlocker " + suffix, "SERVICE_DUE");
        fixtures.machine("CNC", "Router " + suffix, "WORKING");

        String body = mockMvc.perform(get(PATH).with(EquipmentFixtures.as("OBSERVER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> rows = JsonPath.read(body, "$.items[?(@.id == '" + mine + "')]");
        assertEquals(1, rows.size());
        assertEquals("SERVICE_DUE", rows.get(0).get("state"));
        assertEquals("", rows.get(0).get("note"));
        List<Object> others = JsonPath.read(body, "$.items[?(@.name =~ /Router.*/)]");
        assertTrue(others.isEmpty());
    }

    @Test
    void listIsRefusedForSubmittersAndAnonymousCallers() throws Exception {
        mockMvc.perform(get(PATH).with(EquipmentFixtures.as("SUBMITTER"))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTextileCoordinatorAddsAMachineAndOthersCannot() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        String actor = fixtures.coordinatorEmail("Textile");
        String name = "Embroidery machine " + UUID.randomUUID();

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as(actor, "DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("WORKING"))
                .andExpect(jsonPath("$.note").value(""));

        entityManager.flush();
        assertEquals(1, fixtures.auditEntries(actor, "machine.registered").size());
        mockMvc.perform(post(PATH).with(fixtures.coordinatorOf("CNC"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"x\"}"))
                .andExpect(status().isForbidden());
        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_MANAGE) {
            mockMvc.perform(post(PATH).with(EquipmentFixtures.as(role))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"x\"}"))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"%s\"}".formatted(name.toUpperCase())))
                .andExpect(status().isConflict());
        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \" \"}"))
                .andExpect(status().isUnprocessableContent());
    }

    // ---- reports ---------------------------------------------------------------------------------------------

    @Test
    void aFaultReportFlagsTheMachineKeepsThePhotoNameAndNeverPutsTheTextInTheAuditLog() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID id = fixtures.machine("TEXTILE", "Sewing machine", "WORKING");
        String admin = fixtures.insertUser("ADMINISTRATOR");
        String actor = EquipmentFixtures.email("operator");

        mockMvc.perform(multipart(PATH + "/{id}/reports", id)
                        .file(reportPart("{\"kind\": \"FAULT\", \"description\": \"Needle keeps snapping\"}"))
                        .file(new MockMultipartFile("photo", "C:\\fakepath\\needle.jpg", "image/jpeg", new byte[] {1, 2, 3}))
                        .with(EquipmentFixtures.as(actor, "REVIEWER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.machine.id").value(id.toString()))
                .andExpect(jsonPath("$.machine.state").value("FAULT_REPORTED"))
                .andExpect(jsonPath("$.machine.note").value("Needle keeps snapping"))
                .andExpect(jsonPath("$.report.kind").value("FAULT"))
                .andExpect(jsonPath("$.report.description").value("Needle keeps snapping"))
                .andExpect(jsonPath("$.report.photo.name").value("needle.jpg"))
                .andExpect(jsonPath("$.report.by").value(actor))
                .andExpect(jsonPath("$.report.at").isString())
                .andExpect(jsonPath("$.managersNotified[0]").value("Administrators"));

        entityManager.flush();
        List<Map<String, Object>> entries = fixtures.auditEntries(actor, "machine.fault_reported");
        assertEquals(1, entries.size());
        String all = entries.get(0).get("before_text") + " " + entries.get(0).get("after_text");
        assertTrue(all.contains("WORKING") && all.contains("FAULT_REPORTED"), all);
        assertFalse(all.contains("Needle") || all.contains("needle.jpg") || all.contains("Sewing"), all);
        assertEquals(1, fixtures.notificationsFor(admin, "FAULT_REPORTED"));
    }

    @Test
    void aServiceReportWithoutAPhotoFlagsAWorkingMachineAsServiceDue() throws Exception {
        UUID id = fixtures().machine("TEXTILE", "Steam press", "WORKING");

        mockMvc.perform(multipart(PATH + "/{id}/reports", id)
                        .file(reportPart("{\"kind\": \"SERVICE\", \"description\": \"Descale due this month\"}"))
                        .with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.machine.state").value("SERVICE_DUE"))
                .andExpect(jsonPath("$.report.kind").value("SERVICE"))
                .andExpect(jsonPath("$.report.photo").value((Object) null));
    }

    @Test
    void aServiceReportNeverHidesAnEarlierFault() throws Exception {
        UUID id = fixtures().machine("TEXTILE", "Cutter", "FAULT_REPORTED");

        mockMvc.perform(multipart(PATH + "/{id}/reports", id)
                        .file(reportPart("{\"kind\": \"SERVICE\", \"description\": \"Blade service\"}"))
                        .with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.machine.state").value("FAULT_REPORTED"));
    }

    @Test
    void reportsFollowTheRolesAndTheDepartment() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID id = fixtures.machine("TEXTILE", "Loom", "WORKING");
        String json = "{\"kind\": \"FAULT\", \"description\": \"Thread tension is off\"}";

        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_REPORT) {
            mockMvc.perform(multipart(PATH + "/{id}/reports", id).file(reportPart(json)).with(EquipmentFixtures.as(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(multipart(PATH + "/{id}/reports", id).file(reportPart(json)).with(fixtures.coordinatorOf("CNC")))
                .andExpect(status().isForbidden());
        for (String role : EquipmentFixtures.ROLES_THAT_CAN_REPORT) {
            mockMvc.perform(multipart(PATH + "/{id}/reports", id).file(reportPart(json)).with(EquipmentFixtures.as(role)))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(multipart(PATH + "/{id}/reports", id).file(reportPart(json)).with(fixtures.coordinatorOf("Textile")))
                .andExpect(status().isCreated());
    }

    @Test
    void reportingOnAMissingOrForeignMachineIsNotFound() throws Exception {
        UUID cnc = fixtures().machine("CNC", "Router", "WORKING");
        String json = "{\"description\": \"Dead\"}";

        for (UUID id : List.of(UUID.randomUUID(), cnc)) {
            mockMvc.perform(multipart(PATH + "/{id}/reports", id).file(reportPart(json)).with(EquipmentFixtures.as("ADMINISTRATOR")))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void aReportNeedsADescriptionAndAJpgOrPngPhoto() throws Exception {
        UUID id = fixtures().machine("TEXTILE", "Zigzag machine", "WORKING");

        mockMvc.perform(multipart(PATH + "/{id}/reports", id).file(reportPart("{\"description\": \"  \"}"))
                        .with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(multipart(PATH + "/{id}/reports", id).file(reportPart("{\"description\": \"Noisy\"}"))
                        .file(new MockMultipartFile("photo", "notes.pdf", "application/pdf", new byte[] {1}))
                        .with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(multipart(PATH + "/{id}/reports", id).file(reportPart("{\"kind\": \"INSPECTION\", \"description\": \"Noisy\"}"))
                        .with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(PATH + "/{id}/reports", id).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isUnprocessableContent());
    }

    // ---- mark working ----------------------------------------------------------------------------------------

    @Test
    void markingAFaultyMachineWorkingResetsItAndUsesTheNote() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID id = fixtures.machine("TEXTILE", "Binder", "FAULT_REPORTED");
        String actor = fixtures.coordinatorEmail("Textile");

        mockMvc.perform(post(PATH + "/{id}/mark-working", id).with(EquipmentFixtures.as(actor, "DEPARTMENT_COORDINATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"New belt fitted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("WORKING"))
                .andExpect(jsonPath("$.note").value("New belt fitted"));

        entityManager.flush();
        List<Map<String, Object>> entries = fixtures.auditEntries(actor, "machine.marked_working");
        assertEquals(1, entries.size());
        assertFalse(String.valueOf(entries.get(0).get("after_text")).contains("belt"));
    }

    @Test
    void markWorkingWithoutABodyUsesADefaultNote() throws Exception {
        UUID id = fixtures().machine("TEXTILE", "Button sewer", "SERVICE_DUE");

        mockMvc.perform(post(PATH + "/{id}/mark-working", id).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("Marked working."));
    }

    @Test
    void markWorkingIsForManagersOfTheDepartmentAndNeedsAMachineThatIsNotWorking() throws Exception {
        EquipmentFixtures fixtures = fixtures();
        UUID faulty = fixtures.machine("TEXTILE", "Pleater", "FAULT_REPORTED");
        UUID working = fixtures.machine("TEXTILE", "Hemmer", "WORKING");
        UUID cnc = fixtures.machine("CNC", "Lathe", "FAULT_REPORTED");

        for (String role : EquipmentFixtures.ROLES_THAT_CANNOT_MANAGE) {
            mockMvc.perform(post(PATH + "/{id}/mark-working", faulty).with(EquipmentFixtures.as(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH + "/{id}/mark-working", faulty).with(fixtures.coordinatorOf("CNC")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + "/{id}/mark-working", UUID.randomUUID()).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/{id}/mark-working", cnc).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/{id}/mark-working", working).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void textThePlatformCannotStoreIsAValidationError() throws Exception {
        UUID id = fixtures().machine("TEXTILE", "Nul machine", "WORKING");

        mockMvc.perform(post(PATH).with(EquipmentFixtures.as("ADMINISTRATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Bad\\u0000name\"}"))
                .andExpect(status().isUnprocessableContent());
        mockMvc.perform(multipart(PATH + "/{id}/reports", id)
                        .file(reportPart("{\"description\": \"Bad\\u0000text\"}")).with(EquipmentFixtures.as("ADMINISTRATOR")))
                .andExpect(status().isUnprocessableContent());
    }

    private static MockMultipartFile reportPart(String json) {
        return new MockMultipartFile("report", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
    }
}
