package org.unipod.unipoderpbackend.controller.factfinding;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.factfinding.FactFindingResponse;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingResult;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.repository.factfinding.FactFindingRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FactFindingApiIntegrationTest {
    private static final UUID UNKNOWN_BRIEF = UUID.fromString("00000000-0000-0000-0000-00000000dead");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private FactFindingRepository checks;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void officerGetsNotStartedViewWhenNoCheckExists() throws Exception {
        Submission brief = createBrief("CASE-FF-1", "Solar dryer");

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("officer").roles("FACT_FINDING_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.briefTitle").value("Solar dryer"))
                .andExpect(jsonPath("$.trackingReference").value("CASE-FF-1"))
                .andExpect(jsonPath("$.briefType").value("general"))
                .andExpect(jsonPath("$.state").value("NOT_STARTED"))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.officerId").value(nullValue()))
                .andExpect(jsonPath("$.titleClear").value(nullValue()))
                .andExpect(jsonPath("$.titleNote").value(nullValue()))
                .andExpect(jsonPath("$.purposeClear").value(nullValue()))
                .andExpect(jsonPath("$.purposeNote").value(nullValue()))
                .andExpect(jsonPath("$.evidenceEnough").value(nullValue()))
                .andExpect(jsonPath("$.evidenceNote").value(nullValue()))
                .andExpect(jsonPath("$.relevantToHub").value(nullValue()))
                .andExpect(jsonPath("$.relevantNote").value(nullValue()))
                .andExpect(jsonPath("$.missingItems").value(nullValue()))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.version").value(nullValue()));
    }

    @Test
    void officerGetsDraftCheckWithEverySavedField() throws Exception {
        Submission brief = createBrief("CASE-FF-2", "Water pump");
        FactFindingCheck check = new FactFindingCheck(brief.getId(), 1, "officer@example.test");
        check.setTitleClear(true);
        check.setTitleNote("Title is specific");
        check.setPurposeClear(false);
        check.setPurposeNote("Purpose is vague");
        check.setEvidenceEnough(true);
        check.setEvidenceNote("Evidence attached");
        check.setRelevantToHub(false);
        check.setRelevantNote("Outside hub scope");
        check.setMissingItems("Budget, prototype photos");
        checks.saveAndFlush(check);

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("officer").roles("FACT_FINDING_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.briefId").value(brief.getId().toString()))
                .andExpect(jsonPath("$.briefTitle").value("Water pump"))
                .andExpect(jsonPath("$.trackingReference").value("CASE-FF-2"))
                .andExpect(jsonPath("$.briefType").value("general"))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.officerId").value("officer@example.test"))
                .andExpect(jsonPath("$.titleClear").value(true))
                .andExpect(jsonPath("$.titleNote").value("Title is specific"))
                .andExpect(jsonPath("$.purposeClear").value(false))
                .andExpect(jsonPath("$.purposeNote").value("Purpose is vague"))
                .andExpect(jsonPath("$.evidenceEnough").value(true))
                .andExpect(jsonPath("$.evidenceNote").value("Evidence attached"))
                .andExpect(jsonPath("$.relevantToHub").value(false))
                .andExpect(jsonPath("$.relevantNote").value("Outside hub scope"))
                .andExpect(jsonPath("$.missingItems").value("Budget, prototype photos"))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(jsonPath("$.completedAt").value(nullValue()))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void officerGetsCompletedCheckWithResultAndCompletionTime() throws Exception {
        Submission brief = createBrief("CASE-FF-3", "Grain mill");
        FactFindingCheck check = new FactFindingCheck(brief.getId(), 1, "officer@example.test");
        check.setTitleClear(true);
        check.setEvidenceEnough(true);
        check.setRelevantToHub(true);
        check.setResult(FactFindingResult.READY_FOR_REVIEW);
        check.setCompletedAt(Instant.parse("2026-11-11T10:00:00Z"));
        checks.saveAndFlush(check);

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("officer").roles("FACT_FINDING_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.result").value("READY_FOR_REVIEW"))
                .andExpect(jsonPath("$.completedAt").isString())
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.round").value(1))
                .andExpect(jsonPath("$.officerId").value("officer@example.test"));
    }

    @Test
    void latestRoundWins() throws Exception {
        Submission brief = createBrief("CASE-FF-4", "Cold storage");
        FactFindingCheck roundOne = new FactFindingCheck(brief.getId(), 1, "officer-one@example.test");
        roundOne.setTitleNote("round one");
        roundOne.setCompletedAt(Instant.parse("2026-11-10T10:00:00Z"));
        roundOne.setResult(FactFindingResult.NEEDS_MORE_INFORMATION);
        checks.saveAndFlush(roundOne);

        FactFindingCheck roundTwo = new FactFindingCheck(brief.getId(), 2, "officer-two@example.test");
        roundTwo.setTitleNote("round two");
        checks.saveAndFlush(roundTwo);

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("officer").roles("FACT_FINDING_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.round").value(2))
                .andExpect(jsonPath("$.officerId").value("officer-two@example.test"))
                .andExpect(jsonPath("$.titleNote").value("round two"))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.result").value(nullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INNOVATION_COORDINATOR", "ADMINISTRATOR"})
    void coordinatorAndAdministratorCanRead(String role) throws Exception {
        Submission brief = createBrief("CASE-FF-5", "Reader role");

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("reader").roles(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_STARTED"));
    }

    @Test
    void submitterIsForbiddenWithProblemDetail() throws Exception {
        Submission brief = createBrief("CASE-FF-6", "Internal notes");

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("submitter").roles("SUBMITTER")))
                .andExpect(status().isForbidden())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "REVIEWER", "SECTOR_OFFICER", "IP_LIAISON", "OBSERVER"})
    void otherExistingRolesAreForbidden(String role) throws Exception {
        Submission brief = createBrief("CASE-FF-7", "Internal notes");

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("other").roles(role)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));
    }

    @Test
    void unknownBriefReturnsNotFoundProblemDetail() throws Exception {
        mockMvc.perform(get("/cases/{id}/fact-finding", UNKNOWN_BRIEF)
                        .with(user("officer").roles("FACT_FINDING_OFFICER")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Submission not found"))
                .andExpect(jsonPath("$.detail").value("The requested submission was not found."));
    }

    @Test
    void unauthenticatedRequestReturnsUnauthorizedProblemDetail() throws Exception {
        mockMvc.perform(get("/cases/{id}/fact-finding", UNKNOWN_BRIEF))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required."));
    }

    @Test
    void malformedBriefIdReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/cases/not-a-uuid/fact-finding")
                        .with(user("officer").roles("FACT_FINDING_OFFICER")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("A request parameter has an invalid value."));
    }

    @Test
    void readingWithoutCheckWritesNoRow() throws Exception {
        Submission brief = createBrief("CASE-FF-11", "No check");
        Long totalBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fact_finding_checks",
                Long.class
        );

        mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("officer").roles("FACT_FINDING_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_STARTED"));

        Long totalAfter = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fact_finding_checks",
                Long.class
        );
        Long forBrief = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fact_finding_checks WHERE brief_id = ?",
                Long.class,
                brief.getId()
        );

        assertEquals(totalBefore, totalAfter);
        assertEquals(0L, forBrief);
        assertTrue(checks.findTopByBriefIdOrderByRoundDesc(brief.getId()).isEmpty());
    }

    @Test
    void jsonFieldsMatchTheResponseRecordExactly() throws Exception {
        Submission brief = createBrief("CASE-FF-12", "Fields");

        String body = mockMvc.perform(get("/cases/{id}/fact-finding", brief.getId())
                        .with(user("officer").roles("FACT_FINDING_OFFICER")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        Set<String> actual = new HashSet<>(json.propertyNames());
        Set<String> expected = Arrays.stream(FactFindingResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertEquals(expected, actual);
    }

    private Submission createBrief(String code, String title) {
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
        return submissions.saveAndFlush(brief);
    }
}
