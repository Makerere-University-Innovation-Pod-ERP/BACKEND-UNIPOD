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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FactFindingCommandApiIntegrationTest {
    private static final String OFFICER = "officer@example.test";
    private static final String OTHER_OFFICER = "second.officer@example.test";
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000dead");

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
    void officerSavesFirstDraft() throws Exception {
        Submission brief = createBrief("CASE-FF-C1", "Solar dryer", SubmissionStage.FACT_FINDING);

        save(brief, answers("one"), OFFICER)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.state").value("DRAFT"))
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
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.version").value(0));

        assertEquals(1L, countChecks(brief.getId()));
    }

    @Test
    void officerSavesDraftOnForwardedBrief() throws Exception {
        Submission brief = createBrief("CASE-FF-C1F", "Cold room", SubmissionStage.FORWARDED);

        save(brief, answers("forwarded"), OFFICER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.officerId").value(OFFICER));

        assertEquals(1L, countChecks(brief.getId()));
    }

    @Test
    void secondSaveReplacesAnswersAndIncrementsVersion() throws Exception {
        Submission brief = createBrief("CASE-FF-C2", "Water pump", SubmissionStage.FACT_FINDING);
        long version = savedVersion(save(brief, answers("first"), OFFICER));

        Map<String, Object> replacement = withVersion(answers("second"), version);
        save(brief, replacement, OFFICER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleNote").value("Title second"))
                .andExpect(jsonPath("$.purposeNote").value("Purpose second"))
                .andExpect(jsonPath("$.evidenceNote").value("Evidence second"))
                .andExpect(jsonPath("$.relevantNote").value("Relevant second"))
                .andExpect(jsonPath("$.missingItems").value("Missing second"))
                .andExpect(jsonPath("$.officerId").value(OFFICER))
                .andExpect(jsonPath("$.version").value(version + 1));

        assertEquals(1L, countChecks(brief.getId()));
        FactFindingCheck stored = latestCheck(brief.getId());
        assertEquals("Title second", stored.getTitleNote());
        assertEquals(version + 1, stored.getVersion());
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        Submission brief = createBrief("CASE-FF-C3", "Grain mill", SubmissionStage.FACT_FINDING);
        long version = savedVersion(save(brief, answers("first"), OFFICER));
        save(brief, withVersion(answers("second"), version), OFFICER)
                .andExpect(status().isOk());

        save(brief, withVersion(answers("stale"), version), OFFICER)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Submission conflict"))
                .andExpect(jsonPath("$.detail").value(
                        "This fact-finding check was changed by another user. Reload it before saving."
                ));

        assertEquals("Title second", latestCheck(brief.getId()).getTitleNote());
        assertEquals(1L, countChecks(brief.getId()));
    }

    @Test
    void missingVersionOnExistingDraftIsRejected() throws Exception {
        Submission brief = createBrief("CASE-FF-C4", "Grain dryer", SubmissionStage.FACT_FINDING);
        save(brief, answers("first"), OFFICER).andExpect(status().isOk());

        save(brief, answers("unversioned"), OFFICER)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("version is required")));

        assertEquals("Title first", latestCheck(brief.getId()).getTitleNote());
    }

    @Test
    void versionOnFirstSaveIsRejected() throws Exception {
        Submission brief = createBrief("CASE-FF-C5", "Seed press", SubmissionStage.FACT_FINDING);

        save(brief, withVersion(answers("first"), 0L), OFFICER)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("version must not be supplied")));

        assertEquals(0L, countChecks(brief.getId()));
    }

    @Test
    void nullFieldClearsSavedValue() throws Exception {
        Submission brief = createBrief("CASE-FF-C6", "Brick press", SubmissionStage.FACT_FINDING);
        long version = savedVersion(save(brief, answers("first"), OFFICER));

        Map<String, Object> cleared = withVersion(answers("second"), version);
        cleared.put("titleNote", null);
        cleared.put("missingItems", null);
        save(brief, cleared, OFFICER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleNote").value(nullValue()))
                .andExpect(jsonPath("$.missingItems").value(nullValue()))
                .andExpect(jsonPath("$.purposeNote").value("Purpose second"));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertNull(stored.getTitleNote());
        assertNull(stored.getMissingItems());
        assertEquals("Purpose second", stored.getPurposeNote());
    }

    @Test
    void noteLongerThanOneThousandCharactersIsRejected() throws Exception {
        Submission brief = createBrief("CASE-FF-C7", "Long note", SubmissionStage.FACT_FINDING);
        Map<String, Object> body = answers("first");
        body.put("titleNote", "t".repeat(1001));

        save(brief, body, OFFICER)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("titleNote")))
                .andExpect(jsonPath("$.detail").value(containsString("1000")));

        assertEquals(0L, countChecks(brief.getId()));
    }

    @Test
    void missingItemsLongerThanTwoThousandCharactersIsRejected() throws Exception {
        Submission brief = createBrief("CASE-FF-C8", "Long missing items", SubmissionStage.FACT_FINDING);
        Map<String, Object> body = answers("first");
        body.put("missingItems", "m".repeat(2001));

        save(brief, body, OFFICER)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(containsString("missingItems")))
                .andExpect(jsonPath("$.detail").value(containsString("2000")));

        assertEquals(0L, countChecks(brief.getId()));
    }

    @Test
    void valuesAtTheExactLimitsAreAccepted() throws Exception {
        Submission brief = createBrief("CASE-FF-C9", "Exact limits", SubmissionStage.FACT_FINDING);
        Map<String, Object> body = answers("limit");
        body.put("titleNote", "t".repeat(1000));
        body.put("purposeNote", "p".repeat(1000));
        body.put("evidenceNote", "e".repeat(1000));
        body.put("relevantNote", "r".repeat(1000));
        body.put("missingItems", "m".repeat(2000));

        save(brief, body, OFFICER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertEquals(1000, stored.getTitleNote().length());
        assertEquals(2000, stored.getMissingItems().length());
    }

    @Test
    void completedCheckCannotBeEdited() throws Exception {
        Submission brief = createBrief("CASE-FF-C10", "Completed", SubmissionStage.FACT_FINDING);
        FactFindingCheck completed = new FactFindingCheck(brief.getId(), 1, OFFICER);
        completed.setTitleNote("Completed note");
        completed.setResult(FactFindingResult.READY_FOR_REVIEW);
        completed.setCompletedAt(Instant.parse("2026-11-10T10:00:00Z"));
        checks.saveAndFlush(completed);

        save(brief, withVersion(answers("edit"), 0L), OFFICER)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("A completed fact-finding check cannot be edited."));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertEquals("Completed note", stored.getTitleNote());
        assertEquals(FactFindingResult.READY_FOR_REVIEW, stored.getResult());
        assertEquals(Instant.parse("2026-11-10T10:00:00Z"), stored.getCompletedAt());
        assertEquals(0L, stored.getVersion());
    }

    @Test
    void briefInAnotherStageCannotBeSaved() throws Exception {
        Submission brief = createDraftBrief("Draft stage");

        save(brief, answers("first"), OFFICER)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(
                        "Fact-finding answers can only be saved while the case is forwarded or in fact-finding."
                ));

        assertEquals(0L, countChecks(brief.getId()));
    }

    @Test
    void anotherOfficerContinuesTheDraftWithoutTakingOwnership() throws Exception {
        Submission brief = createBrief("CASE-FF-C12", "Handover", SubmissionStage.FACT_FINDING);
        long version = savedVersion(save(brief, answers("first"), OFFICER));

        save(brief, withVersion(answers("second"), version), OTHER_OFFICER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerId").value(OFFICER))
                .andExpect(jsonPath("$.titleNote").value("Title second"));

        assertEquals(OFFICER, latestCheck(brief.getId()).getOfficerId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INNOVATION_COORDINATOR", "ADMINISTRATOR"})
    void coordinatorAndAdministratorCannotWrite(String role) throws Exception {
        Submission brief = createBrief("CASE-FF-C13", "Read only roles", SubmissionStage.FACT_FINDING);

        save(brief, answers("first"), "reader", role)
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));

        assertEquals(0L, countChecks(brief.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMITTER", "RECEPTIONIST", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"})
    void otherExistingRolesCannotWrite(String role) throws Exception {
        Submission brief = createBrief("CASE-FF-C14", "Internal notes", SubmissionStage.FACT_FINDING);

        save(brief, answers("first"), "other", role)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));

        assertEquals(0L, countChecks(brief.getId()));
    }

    @Test
    void unknownBriefReturnsNotFoundProblemDetail() throws Exception {
        mockMvc.perform(put("/cases/{id}/fact-finding", UNKNOWN_BRIEF)
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers("first"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Submission not found"))
                .andExpect(jsonPath("$.detail").value("The requested submission was not found."));
    }

    @Test
    void malformedBriefIdReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/cases/not-a-uuid/fact-finding")
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers("first"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("A request parameter has an invalid value."));
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorizedProblemDetail() throws Exception {
        Submission brief = createBrief("CASE-FF-C15", "Unauthenticated", SubmissionStage.FACT_FINDING);

        mockMvc.perform(put("/cases/{id}/fact-finding", brief.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers("first"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        Submission brief = createBrief("CASE-FF-C16", "Malformed", SubmissionStage.FACT_FINDING);

        mockMvc.perform(put("/cases/{id}/fact-finding", brief.getId())
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"titleNote\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Malformed request body"));
    }

    @Test
    void serverOwnedFieldsInTheBodyAreIgnored() throws Exception {
        Submission brief = createBrief("CASE-FF-C17", "Server owned", SubmissionStage.FACT_FINDING);
        Map<String, Object> body = answers("first");
        body.put("officerId", "intruder@example.test");
        body.put("round", 7);
        body.put("result", "READY_FOR_REVIEW");
        body.put("completedAt", "2026-01-01T00:00:00Z");

        save(brief, body, OFFICER)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officerId").value(OFFICER))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.state").value("DRAFT"));

        FactFindingCheck stored = latestCheck(brief.getId());
        assertEquals(OFFICER, stored.getOfficerId());
        assertEquals(1, stored.getRound());
        assertNull(stored.getResult());
        assertNull(stored.getCompletedAt());
    }

    @Test
    void savedDraftIsReturnedByTheReadEndpoint() throws Exception {
        Submission brief = createBrief("CASE-FF-C18", "Read back", SubmissionStage.FACT_FINDING);
        long version = savedVersion(save(brief, answers("first"), OFFICER));

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("reader").roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.officerId").value(OFFICER))
                .andExpect(jsonPath("$.titleNote").value("Title first"))
                .andExpect(jsonPath("$.purposeNote").value("Purpose first"))
                .andExpect(jsonPath("$.evidenceNote").value("Evidence first"))
                .andExpect(jsonPath("$.relevantNote").value("Relevant first"))
                .andExpect(jsonPath("$.missingItems").value("Missing first"))
                .andExpect(jsonPath("$.version").value(version));
    }

    @Test
    void savingDraftDoesNotChangeTheSubmissionStage() throws Exception {
        Submission brief = createBrief("CASE-FF-C19", "Stage guard", SubmissionStage.FACT_FINDING);

        save(brief, answers("first"), OFFICER).andExpect(status().isOk());

        assertEquals(
                SubmissionStage.FACT_FINDING,
                submissions.findById(brief.getId()).orElseThrow().getStage()
        );
    }

    private ResultActions save(Submission brief, Map<String, Object> body, String principal) throws Exception {
        return save(brief, body, principal, "FACT_FINDING_OFFICER");
    }

    private ResultActions save(
            Submission brief,
            Map<String, Object> body,
            String principal,
            String role
    ) throws Exception {
        return mockMvc.perform(put("/cases/{id}/fact-finding", brief.getId())
                .with(user(principal).roles(role))
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

    private Map<String, Object> answers(String marker) {
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

    private static Map<String, Object> withVersion(Map<String, Object> body, long version) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.put("version", version);
        return copy;
    }

    private long countChecks(UUID briefId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fact_finding_checks WHERE brief_id = ?",
                Long.class,
                briefId
        );
    }

    private FactFindingCheck latestCheck(UUID briefId) {
        return checks.findTopByBriefIdOrderByRoundDesc(briefId).orElseThrow();
    }

    private Submission createBrief(String code, String title, SubmissionStage targetStage) {
        Submission brief = briefEntity(code, title);
        brief.register(code, Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        if (targetStage == SubmissionStage.FACT_FINDING) {
            brief.transitionTo(SubmissionStage.REGISTERED, Instant.parse("2026-11-01T10:00:00Z"));
            brief.transitionTo(SubmissionStage.FACT_FINDING, Instant.parse("2026-11-01T11:00:00Z"));
        } else if (targetStage == SubmissionStage.FORWARDED) {
            Sector sector = sectors.saveAndFlush(new Sector(
                    UUID.randomUUID(),
                    "Sector " + code,
                    "sector-" + code + "@example.test",
                    true,
                    Instant.parse("2026-11-01T08:00:00Z"),
                    Instant.parse("2026-11-01T08:00:00Z")
            ));
            brief.forward(sector, Instant.parse("2026-11-01T10:00:00Z"));
        }
        return submissions.saveAndFlush(brief);
    }

    private Submission createDraftBrief(String title) {
        return submissions.saveAndFlush(briefEntity("CASE-FF-DRAFT", title));
    }

    private static Submission briefEntity(String code, String title) {
        return new Submission(
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
    }
}
