package org.unipod.unipoderpbackend.controller.factfinding;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.factfinding.FactFindingResponse;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.sector.SectorRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FactFindingCompleteApiIntegrationTest {
    private static final String OFFICER = "officer@example.test";
    private static final String OTHER_OFFICER = "second.officer@example.test";
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000dead");
    private static final String ALREADY_COMPLETED = "This fact-finding check is already completed.";
    private static final String NO_CHECK = "Save the fact-finding check before completing it.";
    private static final String STAGE_CONFLICT =
            "Fact-finding can only be completed while the case is forwarded or in fact-finding.";
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
    private ObjectMapper objectMapper;

    @Test
    void officerCompletesOnFactFindingBrief() throws Exception {
        Submission brief = createBrief("CASE-FF-D1", "Solar dryer", SubmissionStage.IN_FACT_FINDING);
        long version = savedVersion(saveAnswers(brief, answers("one"), OFFICER));

        complete(brief.getId(), versionBody(version), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.briefTitle").value("Solar dryer"))
                .andExpect(jsonPath("$.trackingReference").value("CASE-FF-D1"))
                .andExpect(jsonPath("$.briefType").value("general"))
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.officerId").value(OFFICER))
                .andExpect(jsonPath("$.titleClear").value(true))
                .andExpect(jsonPath("$.titleNote").value("Title one"))
                .andExpect(jsonPath("$.purposeClear").value(true))
                .andExpect(jsonPath("$.purposeNote").value("Purpose one"))
                .andExpect(jsonPath("$.evidenceEnough").value(false))
                .andExpect(jsonPath("$.evidenceNote").value("Evidence one"))
                .andExpect(jsonPath("$.relevantToHub").value(true))
                .andExpect(jsonPath("$.relevantNote").value("Relevant one"))
                .andExpect(jsonPath("$.missingItems").value("Missing one"))
                .andExpect(jsonPath("$.version").value(version + 1));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertEquals(FactFindingResult.READY_FOR_REVIEW, stored.getResult());
        assertNotNull(stored.getCompletedAt());
        assertEquals(SubmissionStage.READY_FOR_REVIEW, storedStage(brief.getId()));
    }

    @Test
    void officerCompletesOnForwardedBrief() throws Exception {
        Submission brief = createBrief("CASE-FF-D2", "Cold room", SubmissionStage.FORWARDED);
        long version = savedVersion(saveAnswers(brief, answers("forwarded"), OFFICER));

        complete(brief.getId(), versionBody(version), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("READY_FOR_REVIEW"));

        assertEquals(SubmissionStage.READY_FOR_REVIEW, storedStage(brief.getId()));
        assertEquals("FORWARDED", historyField(brief.getId(), "from_stage"));
    }

    @Test
    void completionRecordsStageHistoryWithActor() throws Exception {
        Submission brief = createBrief("CASE-FF-D3", "Grain mill", SubmissionStage.IN_FACT_FINDING);
        long version = savedVersion(saveAnswers(brief, answers("one"), OFFICER));

        complete(brief.getId(), versionBody(version), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT from_stage, to_stage, changed_by, note FROM case_stage_history WHERE submission_id = ?",
                brief.getId()
        );
        assertEquals("IN_FACT_FINDING", row.get("from_stage"));
        assertEquals("READY_FOR_REVIEW", row.get("to_stage"));
        assertEquals(OFFICER, row.get("changed_by"));
        assertEquals("Fact-finding complete: ready for review.", row.get("note"));
        assertEquals(1L, historyCount(brief.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"titleClear", "purposeClear", "evidenceEnough", "relevantToHub"})
    void unansweredQuestionIsRejectedNamingTheField(String field) throws Exception {
        Submission brief = createBrief("CASE-FF-D4", "Unanswered " + field, SubmissionStage.IN_FACT_FINDING);
        Map<String, Object> body = answers("one");
        body.put(field, null);
        long version = savedVersion(saveAnswers(brief, body, OFFICER));

        complete(brief.getId(), versionBody(version), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(
                        "Answer all four questions before completing: " + field + "."
                ));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertNull(stored.getCompletedAt());
        assertNull(stored.getResult());
        assertEquals(SubmissionStage.IN_FACT_FINDING, storedStage(brief.getId()));
        assertEquals(0L, historyCount(brief.getId()));
    }

    @Test
    void everyUnansweredQuestionIsNamed() throws Exception {
        Submission brief = createBrief("CASE-FF-D5", "Two unanswered", SubmissionStage.IN_FACT_FINDING);
        Map<String, Object> body = answers("one");
        body.put("evidenceEnough", null);
        body.put("relevantToHub", null);
        long version = savedVersion(saveAnswers(brief, body, OFFICER));

        complete(brief.getId(), versionBody(version), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "Answer all four questions before completing: evidenceEnough, relevantToHub."
                ));
    }

    @Test
    void negativeAnswersStillComplete() throws Exception {
        Submission brief = createBrief("CASE-FF-D6", "All false", SubmissionStage.IN_FACT_FINDING);
        Map<String, Object> body = answers("negative");
        body.put("titleClear", false);
        body.put("purposeClear", false);
        body.put("evidenceEnough", false);
        body.put("relevantToHub", false);
        long version = savedVersion(saveAnswers(brief, body, OFFICER));

        complete(brief.getId(), versionBody(version), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.titleClear").value(false))
                .andExpect(jsonPath("$.purposeClear").value(false))
                .andExpect(jsonPath("$.evidenceEnough").value(false))
                .andExpect(jsonPath("$.relevantToHub").value(false));
    }

    @Test
    void notesAreOptionalWhenCompleting() throws Exception {
        Submission brief = createBrief("CASE-FF-D7", "No notes", SubmissionStage.IN_FACT_FINDING);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("titleClear", true);
        body.put("purposeClear", false);
        body.put("evidenceEnough", true);
        body.put("relevantToHub", false);
        body.put("titleNote", "");
        body.put("purposeNote", "");
        body.put("evidenceNote", "");
        body.put("relevantNote", "");
        body.put("missingItems", null);
        long version = savedVersion(saveAnswers(brief, body, OFFICER));

        complete(brief.getId(), versionBody(version), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.missingItems").value(nullValue()));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertEquals("", stored.getTitleNote());
        assertNull(stored.getMissingItems());
    }

    @Test
    void completeWithoutSavedDraftIsConflict() throws Exception {
        Submission brief = createBrief("CASE-FF-D8", "No draft", SubmissionStage.IN_FACT_FINDING);

        complete(brief.getId(), versionBody(0L), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Submission conflict"))
                .andExpect(jsonPath("$.detail").value(NO_CHECK));

        assertEquals(0L, countChecks(brief.getId()));
        assertEquals(SubmissionStage.IN_FACT_FINDING, storedStage(brief.getId()));
    }

    @Test
    void alreadyCompletedCheckIsConflictAndUnchanged() throws Exception {
        Submission brief = createBrief("CASE-FF-D9", "Completed", SubmissionStage.IN_FACT_FINDING);
        FactFindingCheck completed = new FactFindingCheck(brief.getId(), 1, OFFICER);
        completed.setTitleClear(true);
        completed.setPurposeClear(true);
        completed.setEvidenceEnough(true);
        completed.setRelevantToHub(true);
        Instant completedAt = Instant.parse("2026-11-10T10:00:00Z");
        completed.setResult(FactFindingResult.READY_FOR_REVIEW);
        completed.setCompletedAt(completedAt);
        checks.saveAndFlush(completed);

        complete(brief.getId(), versionBody(0L), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(ALREADY_COMPLETED));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertEquals(FactFindingResult.READY_FOR_REVIEW, stored.getResult());
        assertEquals(completedAt, stored.getCompletedAt());
        assertEquals(0L, stored.getVersion());
        assertEquals(SubmissionStage.IN_FACT_FINDING, storedStage(brief.getId()));
        assertEquals(0L, historyCount(brief.getId()));
    }

    @Test
    void missingVersionIsUnprocessable() throws Exception {
        Submission brief = createBrief("CASE-FF-D10", "No version", SubmissionStage.IN_FACT_FINDING);
        saveAnswers(brief, answers("one"), OFFICER);

        complete(brief.getId(), Map.of(), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("version is required")));

        assertNull(latestCheck(brief.getId()).getCompletedAt());
    }

    @Test
    void staleVersionIsConflict() throws Exception {
        Submission brief = createBrief("CASE-FF-D11", "Stale", SubmissionStage.IN_FACT_FINDING);
        long version = savedVersion(saveAnswers(brief, answers("one"), OFFICER));

        complete(brief.getId(), versionBody(version + 5), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(CHANGED_BY_ANOTHER_USER));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertNull(stored.getCompletedAt());
        assertEquals(version, stored.getVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTED", "IN_REVIEW"})
    void briefInOtherStageCannotBeCompleted(String stage) throws Exception {
        Submission brief = createBrief("CASE-FF-D12-" + stage, "Wrong stage", SubmissionStage.valueOf(stage));

        complete(brief.getId(), versionBody(0L), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(STAGE_CONFLICT));

        assertEquals(0L, countChecks(brief.getId()));
        assertEquals(SubmissionStage.valueOf(stage), storedStage(brief.getId()));
    }

    @Test
    void anotherOfficerCompletesWithoutTakingOwnership() throws Exception {
        Submission brief = createBrief("CASE-FF-D13", "Handover", SubmissionStage.IN_FACT_FINDING);
        long version = savedVersion(saveAnswers(brief, answers("one"), OFFICER));

        complete(brief.getId(), versionBody(version), OTHER_OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.officerId").value(OFFICER))
                .andExpect(jsonPath("$.result").value("READY_FOR_REVIEW"));

        assertEquals(OFFICER, latestCheck(brief.getId()).getOfficerId());
        assertEquals(OTHER_OFFICER, historyField(brief.getId(), "changed_by"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INNOVATION_COORDINATOR", "ADMINISTRATOR"})
    void coordinatorAndAdministratorCannotComplete(String role) throws Exception {
        Submission brief = createBrief("CASE-FF-D14", "Read only roles", SubmissionStage.IN_FACT_FINDING);
        long draftVersion = savedVersion(saveAnswers(brief, answers("one"), OFFICER));

        complete(brief.getId(), versionBody(draftVersion), "reader", role)
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));

        assertNull(latestCheck(brief.getId()).getCompletedAt());
        assertEquals(SubmissionStage.IN_FACT_FINDING, storedStage(brief.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTER", "RECEPTIONIST", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"})
    void otherExistingRolesCannotComplete(String role) throws Exception {
        Submission brief = createBrief("CASE-FF-D15", "Internal notes", SubmissionStage.IN_FACT_FINDING);
        long draftVersion = savedVersion(saveAnswers(brief, answers("one"), OFFICER));

        complete(brief.getId(), versionBody(draftVersion), "other", role)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));

        assertNull(latestCheck(brief.getId()).getCompletedAt());
    }

    @Test
    void unknownBriefReturnsNotFoundProblemDetail() throws Exception {
        complete(UNKNOWN_BRIEF, versionBody(0L), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Submission not found"))
                .andExpect(jsonPath("$.detail").value("The requested submission was not found."));
    }

    @Test
    void malformedBriefIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/cases/not-a-uuid/fact-finding/complete")
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("A request parameter has an invalid value."));
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorizedProblemDetail() throws Exception {
        Submission brief = createBrief("CASE-FF-D16", "Unauthenticated", SubmissionStage.IN_FACT_FINDING);
        long draftVersion = savedVersion(saveAnswers(brief, answers("one"), OFFICER));

        mockMvc.perform(post("/cases/{id}/fact-finding/complete", brief.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(versionBody(draftVersion))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        Submission brief = createBrief("CASE-FF-D17", "Malformed", SubmissionStage.IN_FACT_FINDING);

        mockMvc.perform(post("/cases/{id}/fact-finding/complete", brief.getId())
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"version\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void serverOwnedFieldsInTheBodyAreIgnored() throws Exception {
        Submission brief = createBrief("CASE-FF-D18", "Server owned", SubmissionStage.IN_FACT_FINDING);
        long draftVersion = savedVersion(saveAnswers(brief, answers("one"), OFFICER));
        Map<String, Object> body = versionBody(draftVersion);
        body.put("result", "NEEDS_MORE_INFORMATION");
        body.put("completedAt", "2026-01-01T00:00:00Z");
        body.put("officerId", "intruder@example.test");

        complete(brief.getId(), body, OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.officerId").value(OFFICER))
                .andExpect(jsonPath("$.state").value("COMPLETED"));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertEquals(FactFindingResult.READY_FOR_REVIEW, stored.getResult());
        assertEquals(OFFICER, stored.getOfficerId());
        assertNotEquals(Instant.parse("2026-01-01T00:00:00Z"), stored.getCompletedAt());
    }

    @Test
    void completedCheckIsReadableButNoLongerEditable() throws Exception {
        Submission brief = createBrief("CASE-FF-D19", "Read back", SubmissionStage.IN_FACT_FINDING);
        long draftVersion = savedVersion(saveAnswers(brief, answers("one"), OFFICER));
        complete(brief.getId(), versionBody(draftVersion), OFFICER, "FACT_FINDING_OFFICER")
                .andExpect(status().isOk());

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("reader").roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.officerId").value(OFFICER))
                .andExpect(jsonPath("$.titleNote").value("Title one"));

        mockMvc.perform(put("/cases/{id}/fact-finding", brief.getId())
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers("edit"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "Fact-finding answers can only be saved while the case is forwarded or in fact-finding."
                ));

        assertEquals("Title one", latestCheck(brief.getId()).getTitleNote());
    }

    private ResultActions complete(
            UUID briefId,
            Map<String, Object> body,
            String principal,
            String role
    ) throws Exception {
        return mockMvc.perform(post("/cases/{id}/fact-finding/complete", briefId)
                .with(user(principal).roles(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions saveAnswers(Submission brief, Map<String, Object> body, String principal)
            throws Exception {
        return mockMvc.perform(put("/cases/{id}/fact-finding", brief.getId())
                .with(user(principal).roles("FACT_FINDING_OFFICER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long savedVersion(ResultActions actions) throws Exception {
        MvcResult result = actions.andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                FactFindingResponse.class
        ).version();
    }

    private static Map<String, Object> answers(String marker) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("titleClear", true);
        body.put("titleNote", "Title " + marker);
        body.put("purposeClear", true);
        body.put("purposeNote", "Purpose " + marker);
        body.put("evidenceEnough", false);
        body.put("evidenceNote", "Evidence " + marker);
        body.put("relevantToHub", true);
        body.put("relevantNote", "Relevant " + marker);
        body.put("missingItems", "Missing " + marker);
        return body;
    }

    private static Map<String, Object> versionBody(long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        return body;
    }

    private long countChecks(UUID briefId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fact_finding_checks WHERE brief_id = ?",
                Long.class,
                briefId
        );
    }

    private long historyCount(UUID briefId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM case_stage_history WHERE submission_id = ?",
                Long.class,
                briefId
        );
    }

    private Object historyField(UUID briefId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM case_stage_history WHERE submission_id = ?",
                Object.class,
                briefId
        );
    }

    private SubmissionStage storedStage(UUID briefId) {
        return submissions.findById(briefId).orElseThrow().getStage();
    }

    private FactFindingCheck latestCheck(UUID briefId) {
        return checks.findTopByBriefIdOrderByRoundDesc(briefId).orElseThrow();
    }

    private Submission createBrief(String code, String title, SubmissionStage targetStage) {
        Submission brief = new Submission(
                UUID.randomUUID(),
                SubmissionCategory.GENERAL,
                title,
                "Purpose of " + title,
                "Problem addressed by " + title,
                "receptionist@example.test",
                "submitter@example.test",
                null,
                null,
                null,
                null,
                Instant.parse("2026-11-01T08:00:00Z")
        );
        brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
        brief.register(code, Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        if (targetStage == SubmissionStage.FORWARDED
                || targetStage == SubmissionStage.IN_FACT_FINDING
                || targetStage == SubmissionStage.IN_REVIEW) {
            Sector sector = sectors.saveAndFlush(new Sector(
                    UUID.randomUUID(),
                    "Sector " + code,
                    "sector-" + code + "@example.test",
                    true,
                    Instant.parse("2026-11-01T08:00:00Z"),
                    Instant.parse("2026-11-01T08:00:00Z")
            ));
            brief.forward(sector, Instant.parse("2026-11-01T10:00:00Z"));
            if (targetStage == SubmissionStage.IN_FACT_FINDING
                    || targetStage == SubmissionStage.IN_REVIEW) {
                brief.transitionTo(SubmissionStage.IN_FACT_FINDING, Instant.parse("2026-11-01T11:00:00Z"));
            }
            if (targetStage == SubmissionStage.IN_REVIEW) {
                brief.transitionTo(SubmissionStage.IN_REVIEW, Instant.parse("2026-11-01T12:00:00Z"));
            }
        }
        return submissions.saveAndFlush(brief);
    }
}
