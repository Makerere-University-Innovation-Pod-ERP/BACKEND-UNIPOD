package org.unipod.unipoderpbackend.controller.factfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.sector.SectorRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Committed data (no surrounding test transaction): a stale case version is rejected inside main's askRefinement,
 * after this endpoint has completed and flushed the check. The whole transaction rolls back: the check is a draft
 * again, the stage is unchanged, and there is no refinement request and no completion audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FactFindingNeedsInformationCommittedTest {
    private static final String OFFICER = "committed-ni-officer@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private SectorRepository sectors;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> briefs = new ArrayList<>();
    private final List<UUID> sectorIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            // audit_log is append-only (a trigger rejects DELETE); its rows stay, keyed by this random brief id.
            jdbcTemplate.update("DELETE FROM refinement_requested_changes WHERE request_id IN "
                    + "(SELECT id FROM refinement_requests WHERE brief_id = ?)", brief);
            jdbcTemplate.update("DELETE FROM refinement_requests WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM fact_finding_checks WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM notification_outbox WHERE aggregate_id = ?", brief);
            jdbcTemplate.update("DELETE FROM case_stage_history WHERE submission_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        for (UUID sector : sectorIds) {
            jdbcTemplate.update("DELETE FROM sectors WHERE id = ?", sector);
        }
        briefs.clear();
        sectorIds.clear();
    }

    @Test
    void aStaleCaseVersionRollsBackTheCompletedCheck() throws Exception {
        UUID brief = briefInFactFinding();
        String saved = mockMvc.perform(put("/cases/{id}/fact-finding", brief)
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"missingItems\":\"A costed budget\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long version = objectMapper.readTree(saved).get("version").asLong();
        long caseVersion = objectMapper.readTree(mockMvc.perform(get("/cases/{id}", brief)
                        .with(user("coord@example.test").roles("INNOVATION_COORDINATOR")))
                .andReturn().getResponse().getContentAsString()).get("submission").get("version").asLong();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("caseVersion", caseVersion + 5);
        body.put("guidanceNote", "Please add the budget.");
        body.put("dueDate", LocalDate.now(ZoneId.of("Africa/Kampala")).plusDays(7).toString());
        body.put("requestedChanges", List.of("Budget table"));
        mockMvc.perform(post("/cases/{id}/fact-finding/needs-information", brief)
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This case was changed by another user. Reload it before saving."));

        Map<String, Object> check = jdbcTemplate.queryForMap(
                "SELECT result, completed_at, version FROM fact_finding_checks WHERE brief_id = ?", brief);
        assertNull(check.get("result"));
        assertNull(check.get("completed_at"));
        assertEquals(version, ((Number) check.get("version")).longValue());
        assertEquals("IN_FACT_FINDING", jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?", String.class, brief));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refinement_requests WHERE brief_id = ?", Integer.class, brief));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ? "
                + "AND action IN ('submission.fact_finding_completed', 'submission.refinement_requested')",
                Integer.class, brief));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT count(*) FROM case_stage_history WHERE submission_id = ? "
                + "AND to_stage = 'IMPROVEMENT_REQUESTED'", Integer.class, brief));
    }

    private UUID briefInFactFinding() {
        UUID id = transactions.execute(status -> {
            Sector sector = sectors.saveAndFlush(new Sector(UUID.randomUUID(), "Sector " + UUID.randomUUID(),
                    "sector-" + UUID.randomUUID() + "@example.test", true, Instant.parse("2026-11-01T08:00:00Z"),
                    Instant.parse("2026-11-01T08:00:00Z")));
            sectorIds.add(sector.getId());
            Submission brief = new Submission(UUID.randomUUID(), SubmissionCategory.GENERAL, "Committed NI brief",
                    "Purpose", "Problem", "receptionist@example.test", "committed-ni-owner@example.test", null, null,
                    null, null, "committed-ni-owner@example.test", "Owner", Instant.parse("2026-11-01T08:00:00Z"));
            brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
            brief.register("CASE-NC-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            brief.forward(sector, Instant.parse("2026-11-01T10:00:00Z"));
            brief.transitionTo(SubmissionStage.IN_FACT_FINDING, Instant.parse("2026-11-01T11:00:00Z"));
            return submissions.saveAndFlush(brief).getId();
        });
        briefs.add(id);
        return id;
    }
}
