package org.unipod.unipoderpbackend.controller.factfinding;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.sector.SectorRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * POST /cases/{id}/fact-finding/needs-information: the officer completes the check with NEEDS_MORE_INFORMATION and
 * asks the submitter for changes through main's ask, in one transaction. Rejections that happen after the check is
 * completed (a stale case version inside main's ask) are proven with committed data in
 * FactFindingNeedsInformationCommittedTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FactFindingNeedsInformationApiIntegrationTest {
    private static final String PATH = "/cases/{id}/fact-finding/needs-information";
    private static final String OFFICER = "officer@example.test";
    private static final String OTHER_OFFICER = "second.officer@example.test";
    private static final String OWNER = "needs-info-owner@example.test";
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000beef");
    private static final ZoneId HUB_ZONE = ZoneId.of("Africa/Kampala");
    private static final String CHANGED_BY_ANOTHER_USER =
            "This fact-finding check was changed by another user. Reload it before saving.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private SectorRepository sectors;

    @Autowired
    private FactFindingRepository checks;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    // ---- 1-3: the outcome ------------------------------------------------------------------

    @Test
    void theOfficerCompletesTheCheckAndAsksForChangesInOneStep() throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("titleClear", true);
        draft.put("evidenceEnough", false);
        draft.put("missingItems", "A costed budget");
        long version = saveDraft(brief, draft, OFFICER);
        LocalDate due = today().plusDays(10);

        // A different officer records the outcome: officerId stays the one who started the draft.
        needsInformation(brief, body(version, caseVersion(brief), "Please add a costed budget.", due,
                List.of("Budget table", "Letter of support")), officer(OTHER_OFFICER))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("NEEDS_MORE_INFORMATION"))
                .andExpect(jsonPath("$.completedAt").value(notNullValue()))
                .andExpect(jsonPath("$.officerId").value(OFFICER))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.titleClear").value(true))
                .andExpect(jsonPath("$.purposeClear").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.missingItems").value("A costed budget"));

        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
        Map<String, Object> request = jdbcTemplate.queryForMap(
                "SELECT id, cycle, source, status, due_date FROM refinement_requests WHERE brief_id = ?", brief.getId());
        assertEquals(1, ((Number) request.get("cycle")).intValue());
        assertEquals("FACT_FINDING", request.get("source"));
        assertEquals("OPEN", request.get("status"));
        assertEquals(due, ((java.sql.Date) request.get("due_date")).toLocalDate());
        assertEquals(List.of(Map.of("position", 1, "text", "Budget table"),
                        Map.of("position", 2, "text", "Letter of support")),
                jdbcTemplate.queryForList("SELECT position, text FROM refinement_requested_changes "
                        + "WHERE request_id = ? ORDER BY position", request.get("id")));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM case_stage_history
                WHERE submission_id = ? AND from_stage = 'IN_FACT_FINDING' AND to_stage = 'IMPROVEMENT_REQUESTED'
                """, Integer.class, brief.getId()));
        List<Map<String, Object>> audit = jdbcTemplate.queryForList("""
                SELECT after_value::text AS details FROM audit_log
                WHERE entity_id = ? AND action = 'submission.fact_finding_completed'
                """, brief.getId());
        assertEquals(1, audit.size());
        JsonNode details = objectMapper.readTree((String) audit.getFirst().get("details"));
        assertEquals(Set.of("round", "result", "version"), new HashSet<>(details.propertyNames()));
        assertEquals("NEEDS_MORE_INFORMATION", details.get("result").asString());
        assertEquals(1, details.get("round").asInt());
        FactFindingCheck stored = checks.findTopByBriefIdOrderByRoundDesc(brief.getId()).orElseThrow();
        assertEquals(FactFindingResult.NEEDS_MORE_INFORMATION, stored.getResult());
        assertEquals(OFFICER, stored.getOfficerId());
    }

    @Test
    void aNoteAloneIsEnoughButNeitherNotesNorMissingItemsIsRejected() throws Exception {
        Submission withNote = brief(SubmissionStage.IN_FACT_FINDING);
        long noteVersion = saveDraft(withNote, Map.of("purposeNote", "The purpose is unclear."), OFFICER);
        needsInformation(withNote, valid(withNote, noteVersion), officer(OFFICER)).andExpect(status().isOk());

        Submission empty = brief(SubmissionStage.IN_FACT_FINDING);
        long emptyVersion = saveDraft(empty, Map.of("titleClear", true, "titleNote", "   "), OFFICER);
        Snapshot before = snapshot(empty);
        needsInformation(empty, valid(empty, emptyVersion), officer(OFFICER))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("missingItems")))
                .andExpect(jsonPath("$.detail").value(containsString("Add at least one missing item or note.")));
        assertEquals(before, snapshot(empty));
    }

    @Test
    void theFourAnswersAreOptional() throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        long version = saveDraft(brief, Map.of("missingItems", "Supervisor contact"), OFFICER);

        needsInformation(brief, valid(brief, version), officer(OFFICER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NEEDS_MORE_INFORMATION"))
                .andExpect(jsonPath("$.titleClear").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.relevantToHub").value(org.hamcrest.Matchers.nullValue()));
    }

    // ---- 4-6: stage, draft and version rules -----------------------------------------------

    @Test
    void aForwardedBriefGetsTheClearMessage() throws Exception {
        Submission brief = brief(SubmissionStage.FORWARDED);
        long version = saveDraft(brief, Map.of("missingItems", "Budget"), OFFICER);
        Snapshot before = snapshot(brief);

        needsInformation(brief, valid(brief, version), officer(OFFICER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Start the first check before asking for more information."));
        assertEquals(before, snapshot(brief));
    }

    @ParameterizedTest
    @EnumSource(value = SubmissionStage.class,
            names = {"READY_FOR_REVIEW", "IN_REVIEW", "IMPROVEMENT_REQUESTED", "RESUBMITTED", "CLOSED", "SUBMITTED"})
    void otherStagesAreAConflict(SubmissionStage stage) throws Exception {
        Submission brief = brief(stage);
        FactFindingCheck draft = new FactFindingCheck(brief.getId(), 1, OFFICER);
        draft.setMissingItems("Budget");
        draft = checks.saveAndFlush(draft);
        Snapshot before = snapshot(brief);

        needsInformation(brief, valid(brief, draft.getVersion()), officer(OFFICER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("More information can only be asked for while the case is in fact-finding."));
        assertEquals(before, snapshot(brief));
    }

    @Test
    void withoutASavedDraftItIsAConflict() throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        Snapshot before = snapshot(brief);

        needsInformation(brief, valid(brief, 0L), officer(OFFICER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Save the fact-finding check before asking for more information."));
        assertEquals(before, snapshot(brief));
    }

    @ParameterizedTest
    @EnumSource(FactFindingResult.class)
    void anAlreadyCompletedCheckIsAConflict(FactFindingResult result) throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        FactFindingCheck done = new FactFindingCheck(brief.getId(), 1, OFFICER);
        done.setMissingItems("Budget");
        done.complete(result, Instant.parse("2026-11-15T09:00:00Z"));
        done = checks.saveAndFlush(done);
        Snapshot before = snapshot(brief);

        needsInformation(brief, valid(brief, done.getVersion()), officer(OFFICER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This fact-finding check is already completed."));
        assertEquals(before, snapshot(brief));
        assertEquals(result, checks.findTopByBriefIdOrderByRoundDesc(brief.getId()).orElseThrow().getResult());
    }

    @Test
    void missingOrStaleVersionsAreRejected() throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        long version = saveDraft(brief, Map.of("missingItems", "Budget"), OFFICER);
        Snapshot before = snapshot(brief);

        Map<String, Object> noVersion = valid(brief, version);
        noVersion.put("version", null);
        needsInformation(brief, noVersion, officer(OFFICER))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("version")));
        needsInformation(brief, valid(brief, version + 3), officer(OFFICER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(CHANGED_BY_ANOTHER_USER));
        Map<String, Object> noCaseVersion = valid(brief, version);
        noCaseVersion.put("caseVersion", null);
        needsInformation(brief, noCaseVersion, officer(OFFICER))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("caseVersion")));
        assertEquals(before, snapshot(brief));
    }

    // ---- 7: field validation -------------------------------------------------------------------

    @Test
    void fieldsAreValidatedAndNothingIsWritten() throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        long version = saveDraft(brief, Map.of("missingItems", "Budget"), OFFICER);
        Snapshot before = snapshot(brief);
        long caseVersion = caseVersion(brief);
        List<String> fiftyOne = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            fiftyOne.add("Change " + i);
        }
        Map<String, Object[]> invalid = new LinkedHashMap<>();
        invalid.put("guidanceNote", new Object[] {"   ", today(), List.of("One")});
        invalid.put("guidanceNote ", new Object[] {"x".repeat(2001), today(), List.of("One")});
        invalid.put("dueDate", new Object[] {"Guidance", today().minusDays(1), List.of("One")});
        invalid.put("requestedChanges", new Object[] {"Guidance", today(), List.of()});
        invalid.put("requestedChanges ", new Object[] {"Guidance", today(), fiftyOne});
        invalid.put("requestedChanges  ", new Object[] {"Guidance", today(), List.of("One", "  ")});
        invalid.put("requestedChanges   ", new Object[] {"Guidance", today(), List.of("y".repeat(501))});
        for (Map.Entry<String, Object[]> entry : invalid.entrySet()) {
            Object[] values = entry.getValue();
            @SuppressWarnings("unchecked")
            List<String> changes = (List<String>) values[2];
            needsInformation(brief, body(version, caseVersion, (String) values[0], (LocalDate) values[1], changes),
                    officer(OFFICER))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value(containsString(entry.getKey().trim())));
        }
        Map<String, Object> noDueDate = body(version, caseVersion, "Guidance", null, List.of("One"));
        needsInformation(brief, noDueDate, officer(OFFICER))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("dueDate")));
        Map<String, Object> noChanges = body(version, caseVersion, "Guidance", today(), null);
        needsInformation(brief, noChanges, officer(OFFICER))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("requestedChanges")));
        assertEquals(before, snapshot(brief));
    }

    @Test
    void theLimitsAreAcceptedAfterTrimming() throws Exception {
        List<String> fifty = new ArrayList<>();
        for (int i = 0; i < 49; i++) {
            fifty.add("Change " + i);
        }
        fifty.add("  " + "z".repeat(500) + "  ");
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        long version = saveDraft(brief, Map.of("missingItems", "Budget"), OFFICER);

        needsInformation(brief, body(version, caseVersion(brief), "  " + "g".repeat(2000) + "  ", today(), fifty),
                officer(OFFICER))
                .andExpect(status().isOk());

        assertEquals("g".repeat(2000), jdbcTemplate.queryForObject(
                "SELECT guidance_note FROM refinement_requests WHERE brief_id = ?", String.class, brief.getId()));
        assertEquals(today(), jdbcTemplate.queryForObject(
                "SELECT due_date FROM refinement_requests WHERE brief_id = ?", java.sql.Date.class, brief.getId())
                .toLocalDate());
        List<String> stored = jdbcTemplate.queryForList("""
                SELECT c.text FROM refinement_requested_changes c JOIN refinement_requests r ON r.id = c.request_id
                WHERE r.brief_id = ? ORDER BY c.position
                """, String.class, brief.getId());
        assertEquals(50, stored.size());
        assertEquals("z".repeat(500), stored.get(49));
    }

    // ---- 8-9: security and the body ------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"INNOVATION_COORDINATOR", "ADMINISTRATOR", "REVIEWER", "SUBMITTER", "RECEPTIONIST",
            "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"})
    void everyOtherRoleIsForbidden(String role) throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        long version = saveDraft(brief, Map.of("missingItems", "Budget"), OFFICER);
        Snapshot before = snapshot(brief);

        needsInformation(brief, valid(brief, version), user("someone@example.test").roles(role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
        assertEquals(before, snapshot(brief));
    }

    @Test
    void unauthenticatedUnknownAndMalformedRequests() throws Exception {
        mockMvc.perform(post(PATH, UNKNOWN_BRIEF).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        needsInformation(UNKNOWN_BRIEF, Map.of("version", 0, "caseVersion", 0), officer(OFFICER))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/cases/not-a-uuid/fact-finding/needs-information").with(officer(OFFICER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        mockMvc.perform(post(PATH, brief.getId()).with(officer(OFFICER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void serverOwnedFieldsInTheBodyAreIgnored() throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        long version = saveDraft(brief, Map.of("missingItems", "Budget"), OFFICER);
        Map<String, Object> body = valid(brief, version);
        body.put("result", "READY_FOR_REVIEW");
        body.put("completedAt", "2020-01-01T00:00:00Z");
        body.put("officerId", "attacker@example.test");
        body.put("round", 7);
        body.put("source", "COORDINATOR");
        body.put("cycle", 9);

        needsInformation(brief, body, officer(OFFICER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("NEEDS_MORE_INFORMATION"))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.officerId").value(OFFICER));
        Map<String, Object> request = jdbcTemplate.queryForMap(
                "SELECT cycle, source FROM refinement_requests WHERE brief_id = ?", brief.getId());
        assertEquals(1, ((Number) request.get("cycle")).intValue());
        assertEquals("FACT_FINDING", request.get("source"));
    }

    // ---- 10-12: afterwards -----------------------------------------------------------------------

    @Test
    void afterwardsTheCheckIsFinalAndTheRequestReachesTheQueueAndTheSubmitter() throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        long version = saveDraft(brief, Map.of("missingItems", "Budget"), OFFICER);
        LocalDate due = today().plusDays(6);
        needsInformation(brief, body(version, caseVersion(brief), "Please add the budget.", due,
                List.of("Budget table", "Unit costs")), officer(OFFICER))
                .andExpect(status().isOk());
        long completedVersion = checks.findTopByBriefIdOrderByRoundDesc(brief.getId()).orElseThrow().getVersion();

        mockMvc.perform(put("/cases/{id}/fact-finding", brief.getId()).with(officer(OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("missingItems", "More", "version",
                                completedVersion))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/cases/{id}/fact-finding/complete", brief.getId()).with(officer(OFFICER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("version", completedVersion))))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId()).with(officer(OFFICER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("NEEDS_MORE_INFORMATION"));
        mockMvc.perform(get("/queues/refinement").with(coordinator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items", hasSize(1)))
                .andExpect(jsonPath("$.rows.items[0].briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.rows.items[0].state").value("WAITING_FOR_SUBMITTER"))
                .andExpect(jsonPath("$.rows.items[0].source").value("FACT_FINDING"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[0].text").value("Budget table"))
                .andExpect(jsonPath("$.rows.items[0].requestedChanges[1].text").value("Unit costs"));
        mockMvc.perform(get("/cases/{id}/my-improvement", brief.getId()).with(user(OWNER).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openRequest.guidanceNote").value("Please add the budget."))
                .andExpect(jsonPath("$.openRequest.dueDate").value(due.toString()))
                .andExpect(jsonPath("$.openRequest.requestedChanges[0].position").value(1))
                .andExpect(jsonPath("$.openRequest.requestedChanges[0].text").value("Budget table"))
                .andExpect(jsonPath("$.openRequest.requestedChanges[1].text").value("Unit costs"));
    }

    @Test
    void theSubmitterAnswersThroughMainsImprovedVersionEndToEnd() throws Exception {
        Submission brief = answeredAfterNeedsInformation();

        assertEquals("RESUBMITTED", stage(brief));
        assertEquals("RESPONDED", jdbcTemplate.queryForObject(
                "SELECT status FROM refinement_requests WHERE brief_id = ?", String.class, brief.getId()));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT version_number FROM case_revisions WHERE brief_id = ?", Integer.class, brief.getId()));
    }

    /**
     * CHARACTERIZATION of a known product question, not an endorsement: after a NEEDS_MORE_INFORMATION outcome and the
     * submitter's answer, the coordinator's accept still succeeds, because FactFindingGuard accepts any completed check
     * whatever its result. If the product decides the officer must re-check first, this test should change.
     */
    @Test
    void knownProductQuestion_acceptStillSucceedsAfterNeedsMoreInformationBecauseTheGuardAcceptsAnyCompletedCheck()
            throws Exception {
        Submission brief = answeredAfterNeedsInformation();
        JsonNode requests = json(mockMvc.perform(get("/cases/{id}/refinement-history", brief.getId())
                .with(coordinator())).andExpect(status().isOk()).andReturn()).get("requests");
        long revisionId = requests.get(0).get("revision").get("id").asLong();

        mockMvc.perform(post("/cases/{id}/revisions/{revisionId}/accept", brief.getId(), revisionId)
                        .with(coordinator()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("caseVersion", caseVersion(brief)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefStage").value("IN_REVIEW"));
        assertEquals(FactFindingResult.NEEDS_MORE_INFORMATION,
                checks.findTopByBriefIdOrderByRoundDesc(brief.getId()).orElseThrow().getResult());
    }

    // ---- 14: contract ----------------------------------------------------------------------------

    @Test
    void responseFieldNamesMatchTheRecordExactly() throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        long version = saveDraft(brief, Map.of("missingItems", "Budget"), OFFICER);

        JsonNode response = json(needsInformation(brief, valid(brief, version), officer(OFFICER))
                .andExpect(status().isOk()).andReturn());

        assertEquals(Set.of("briefId", "briefTitle", "trackingReference", "briefType", "round", "officerId",
                "titleClear", "titleNote", "purposeClear", "purposeNote", "evidenceEnough", "evidenceNote",
                "relevantToHub", "relevantNote", "missingItems", "result", "completedAt", "state", "version"),
                new HashSet<>(response.propertyNames()));
        assertFalse(response.toString().contains("guidanceNote"));
    }

    // ---- helpers ---------------------------------------------------------------------------------

    record Snapshot(String stage, Object checkResult, Object completedAt, int requests, int completedAudit) {
    }

    private Snapshot snapshot(Submission brief) {
        entityManager.flush();
        Map<String, Object> check = jdbcTemplate.queryForList(
                "SELECT result, completed_at FROM fact_finding_checks WHERE brief_id = ?", brief.getId())
                .stream().findFirst().orElse(Map.of());
        return new Snapshot(stage(brief), check.get("result"), check.get("completed_at"),
                jdbcTemplate.queryForObject("SELECT count(*) FROM refinement_requests WHERE brief_id = ?",
                        Integer.class, brief.getId()),
                jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE entity_id = ? "
                        + "AND action = 'submission.fact_finding_completed'", Integer.class, brief.getId()));
    }

    private Submission answeredAfterNeedsInformation() throws Exception {
        Submission brief = brief(SubmissionStage.IN_FACT_FINDING);
        long version = saveDraft(brief, Map.of("missingItems", "Budget"), OFFICER);
        needsInformation(brief, valid(brief, version), officer(OFFICER)).andExpect(status().isOk());
        mockMvc.perform(post("/cases/{id}/improved-version", brief.getId()).with(user(OWNER).roles("SUBMITTER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("note", "Added the budget table."))))
                .andExpect(status().isCreated());
        return brief;
    }

    private ResultActions needsInformation(Submission brief, Map<String, ?> body, RequestPostProcessor caller)
            throws Exception {
        return needsInformation(brief.getId(), body, caller);
    }

    private ResultActions needsInformation(UUID briefId, Map<String, ?> body, RequestPostProcessor caller)
            throws Exception {
        return mockMvc.perform(post(PATH, briefId).with(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Map<String, Object> valid(Submission brief, long version) throws Exception {
        return body(version, caseVersion(brief), "Please add the missing details.", today().plusDays(7),
                List.of("Add the missing details"));
    }

    private static Map<String, Object> body(Long version, Long caseVersion, String guidance, LocalDate dueDate,
            List<String> changes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("caseVersion", caseVersion);
        body.put("guidanceNote", guidance);
        body.put("dueDate", dueDate == null ? null : dueDate.toString());
        body.put("requestedChanges", changes);
        return body;
    }

    private long saveDraft(Submission brief, Map<String, Object> answers, String officer) throws Exception {
        MvcResult result = mockMvc.perform(put("/cases/{id}/fact-finding", brief.getId()).with(officer(officer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("version").asLong();
    }

    private long caseVersion(Submission brief) throws Exception {
        entityManager.flush();
        return json(mockMvc.perform(get("/cases/{id}", brief.getId()).with(coordinator()))
                .andExpect(status().isOk()).andReturn()).get("submission").get("version").asLong();
    }

    private String stage(Submission brief) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT stage FROM submissions WHERE id = ?", String.class, brief.getId());
    }

    /**
     * A registered general brief owned by OWNER (with email, so main's improved-version accepts the answer), forwarded
     * to a sector and moved with the model's lifecycle transitions, the way the other fact-finding tests build briefs.
     */
    private Submission brief(SubmissionStage target) {
        Submission brief = new Submission(UUID.randomUUID(), SubmissionCategory.GENERAL, "Needs information brief",
                "Purpose", "Problem", "receptionist@example.test", OWNER, null, null, null, null, OWNER,
                "Needs Info Owner", Instant.parse("2026-11-01T08:00:00Z"));
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register("CASE-NI-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        if (target == SubmissionStage.SUBMITTED) {
            return submissions.saveAndFlush(brief);
        }
        Sector sector = sectors.saveAndFlush(new Sector(UUID.randomUUID(), "Sector " + UUID.randomUUID(),
                "sector-" + UUID.randomUUID() + "@example.test", true, Instant.parse("2026-11-01T08:00:00Z"),
                Instant.parse("2026-11-01T08:00:00Z")));
        brief.forward(sector, Instant.parse("2026-11-01T10:00:00Z"));
        Instant time = Instant.parse("2026-11-01T11:00:00Z");
        List<SubmissionStage> path = switch (target) {
            case FORWARDED -> List.of();
            case IN_FACT_FINDING -> List.of(SubmissionStage.IN_FACT_FINDING);
            case READY_FOR_REVIEW -> List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.READY_FOR_REVIEW);
            case IN_REVIEW -> List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW);
            case IMPROVEMENT_REQUESTED -> List.of(SubmissionStage.IN_FACT_FINDING,
                    SubmissionStage.IMPROVEMENT_REQUESTED);
            case RESUBMITTED -> List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.IMPROVEMENT_REQUESTED,
                    SubmissionStage.RESUBMITTED);
            case CLOSED -> List.of(SubmissionStage.IN_FACT_FINDING, SubmissionStage.IN_REVIEW, SubmissionStage.CLOSED);
            default -> throw new IllegalArgumentException("No test path to " + target);
        };
        for (SubmissionStage next : path) {
            brief.transitionTo(next, time);
        }
        return submissions.saveAndFlush(brief);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static LocalDate today() {
        return LocalDate.now(HUB_ZONE);
    }

    private static RequestPostProcessor officer(String email) {
        return user(email).roles("FACT_FINDING_OFFICER");
    }

    private static RequestPostProcessor coordinator() {
        return user("coord@example.test").roles("INNOVATION_COORDINATOR");
    }
}
