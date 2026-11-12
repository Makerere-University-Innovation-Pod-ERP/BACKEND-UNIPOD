package org.unipod.unipoderpbackend.controller.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Checks the rows written to the platform audit log; audit_log is append-only, so reads only. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerAuditIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @Test
    void creatingAPartnerWritesAnAuditEntryWithoutABeforeValue() throws Exception {
        String actor = actorEmail();
        String name = "Audit Create " + shortId();

        String id = createPartner(actor, name);

        Map<String, Object> entry = onlyEntry(actor, "partner.created");
        assertEquals("partner", entry.get("entity_type"));
        assertEquals(UUID.fromString(id), entry.get("entity_id"));
        assertEquals("ADMINISTRATOR", entry.get("actor_role"));
        assertNull(entry.get("before_value"));
        assertEquals(name, entry.get("after_name"));
        assertEquals("FUNDER", entry.get("after_type"));
    }

    @Test
    void updatingAPartnerRecordsTheValuesBeforeAndAfter() throws Exception {
        String actor = actorEmail();
        String id = createPartner(actor, "Audit Update " + shortId());

        mockMvc.perform(MockMvcRequestBuilders.patch("/operations/partners/{id}", id)
                        .with(admin(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"type\": \"ACADEMIC_PARTNER\", "
                                + "\"notes\": \"Secret note text\"}"))
                .andExpect(status().isOk());

        Map<String, Object> entry = onlyEntry(actor, "partner.updated");
        assertEquals(UUID.fromString(id), entry.get("entity_id"));
        assertEquals("FUNDER", JsonPath.read((String) entry.get("before_text"), "$.type"));
        assertEquals("ACADEMIC_PARTNER", entry.get("after_type"));
        assertEquals("true", entry.get("after_notes_changed"));
        assertEquals("false", entry.get("after_contact_changed"));
        assertNoPersonalData(entry);
    }

    @Test
    void changingOnlyContactDetailsWritesAnEntryWithoutTheValues() throws Exception {
        String actor = actorEmail();
        String id = createPartner(actor, "Audit Contacts " + shortId());

        mockMvc.perform(MockMvcRequestBuilders.patch("/operations/partners/{id}", id)
                        .with(admin(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"contactPerson\": \"Secret Person\", "
                                + "\"contactEmail\": \"secret.person@partner.test\"}"))
                .andExpect(status().isOk());

        Map<String, Object> entry = onlyEntry(actor, "partner.updated");
        assertEquals("true", entry.get("after_contact_changed"));
        assertEquals("false", entry.get("after_notes_changed"));
        assertNoPersonalData(entry);
    }

    @Test
    void changingOnlyNotesRecordsNotesChangedWithoutTheText() throws Exception {
        String actor = actorEmail();
        String id = createPartner(actor, "Audit Notes Only " + shortId());

        mockMvc.perform(MockMvcRequestBuilders.patch("/operations/partners/{id}", id)
                        .with(admin(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"notes\": \"Secret note text\"}"))
                .andExpect(status().isOk());

        Map<String, Object> entry = onlyEntry(actor, "partner.updated");
        assertEquals("true", entry.get("after_notes_changed"));
        assertEquals("false", entry.get("after_contact_changed"));
        assertNoPersonalData(entry);
    }

    @Test
    void createdEntriesDoNotCarryContactDetailsOrNotes() throws Exception {
        String actor = actorEmail();
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/operations/partners")
                        .with(admin(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Audit Contacts Create " + shortId() + "\", \"type\": \"FUNDER\", "
                                + "\"contactPerson\": \"Secret Person\", "
                                + "\"contactEmail\": \"secret.person@partner.test\", "
                                + "\"notes\": \"Secret note text\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals("Secret Person", JsonPath.read(body, "$.contactPerson"));

        Map<String, Object> entry = onlyEntry(actor, "partner.created");
        assertEquals("false", entry.get("after_contact_changed"));
        assertEquals("false", entry.get("after_notes_changed"));
        assertNoPersonalData(entry);
    }

    @Test
    void recordingAMouRenewalRecordsTheOldAndNewDates() throws Exception {
        String actor = actorEmail();
        String id = createPartner(actor, "Audit Mou " + shortId());
        LocalDate first = LocalDate.now(clock).plusDays(30);
        LocalDate renewed = LocalDate.now(clock).plusDays(400);
        mockMvc.perform(MockMvcRequestBuilders.put("/operations/partners/{id}/mou", id)
                        .with(admin(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"renewalDate\": \"" + first + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.put("/operations/partners/{id}/mou", id)
                        .with(admin(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"renewalDate\": \"" + renewed + "\", \"documentReference\": \"MOU-9\"}"))
                .andExpect(status().isOk());

        List<Map<String, Object>> entries = entries(actor, "partner.mou_renewed");
        assertEquals(2, entries.size());
        Map<String, Object> second = entries.stream()
                .filter(entry -> renewed.toString().equals(entry.get("after_date")))
                .findFirst().orElseThrow();
        assertEquals(first.toString(), second.get("before_date"));
        assertEquals("MOU-9", second.get("after_ref"));
    }

    @Test
    void requestsThatChangeNothingWriteNoAuditEntry() throws Exception {
        String actor = actorEmail();
        String id = createPartner(actor, "Audit No Change " + shortId());

        mockMvc.perform(MockMvcRequestBuilders.patch("/operations/partners/{id}", id)
                        .with(admin(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0}"))
                .andExpect(status().isOk());

        assertEquals(0, entries(actor, "partner.updated").size());
    }

    @Test
    void rejectedRequestsWriteNoAuditEntries() throws Exception {
        String actor = actorEmail();
        String name = "Audit Rejected " + shortId();
        String id = createPartner(actor, name);
        long before = countFor(actor);

        // duplicate name, unknown partner, stale version, invalid value
        mockMvc.perform(MockMvcRequestBuilders.post("/operations/partners")
                        .with(admin(actor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"type\": \"FUNDER\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(MockMvcRequestBuilders.patch("/operations/partners/{id}", UUID.randomUUID())
                        .with(admin(actor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"notes\": \"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(MockMvcRequestBuilders.patch("/operations/partners/{id}", id)
                        .with(admin(actor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 7, \"notes\": \"x\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(MockMvcRequestBuilders.put("/operations/partners/{id}/mou", id)
                        .with(admin(actor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableContent());

        assertEquals(before, countFor(actor));
    }

    @Test
    void forbiddenRolesWriteNoAuditEntries() throws Exception {
        String actor = actorEmail();

        mockMvc.perform(MockMvcRequestBuilders.post("/operations/partners")
                        .with(user(actor).roles("OBSERVER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Audit Forbidden " + shortId() + "\", \"type\": \"FUNDER\"}"))
                .andExpect(status().isForbidden());

        assertEquals(0, countFor(actor));
    }

    private static void assertNoPersonalData(Map<String, Object> entry) {
        for (String column : new String[] {"before_text", "after_text"}) {
            String json = String.valueOf(entry.get(column));
            assertFalse(json.contains("Secret Person"), json);
            assertFalse(json.contains("secret.person@partner.test"), json);
            assertFalse(json.contains("Secret note text"), json);
            assertFalse(json.contains("contactPerson") || json.contains("contactEmail"), json);
            assertFalse(json.contains("\"notes\""), json);
        }
    }

    private String createPartner(String actor, String name) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/operations/partners")
                        .with(admin(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + name + "\", \"type\": \"FUNDER\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private Map<String, Object> onlyEntry(String actor, String action) {
        List<Map<String, Object>> entries = entries(actor, action);
        assertEquals(1, entries.size());
        return entries.get(0);
    }

    private List<Map<String, Object>> entries(String actor, String action) {
        entityManager.flush();
        return jdbc.queryForList("""
                SELECT entity_type, entity_id, actor_role, before_value,
                       before_value ->> 'renewalDate' AS before_date,
                       after_value ->> 'name' AS after_name,
                       after_value ->> 'type' AS after_type,
                       after_value ->> 'renewalDate' AS after_date,
                       after_value ->> 'documentReference' AS after_ref,
                       after_value ->> 'contactChanged' AS after_contact_changed,
                       after_value ->> 'notesChanged' AS after_notes_changed,
                       before_value::text AS before_text,
                       after_value::text AS after_text
                FROM audit_log WHERE actor = ? AND action = ?
                """, actor, action);
    }

    private long countFor(String actor) {
        entityManager.flush();
        Long count = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE actor = ?", Long.class, actor);
        return count == null ? 0 : count;
    }

    private static RequestPostProcessor admin(String actor) {
        return user(actor).roles("ADMINISTRATOR");
    }

    private static String actorEmail() {
        return "audit-" + UUID.randomUUID() + "@test.local";
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
