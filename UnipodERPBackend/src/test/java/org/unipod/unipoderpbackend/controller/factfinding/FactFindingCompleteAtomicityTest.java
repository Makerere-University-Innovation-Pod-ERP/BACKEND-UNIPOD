package org.unipod.unipoderpbackend.controller.factfinding;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.unipod.unipoderpbackend.dto.factfinding.FactFindingResponse;
import org.unipod.unipoderpbackend.model.submission.CaseStageHistory;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs without a surrounding test transaction so the service commits or rolls back for real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FactFindingCompleteAtomicityTest {
    private static final String OFFICER = "officer@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @MockitoSpyBean
    private CaseStageHistoryRepository historyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> createdBriefs = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID briefId : createdBriefs) {
            jdbcTemplate.update("DELETE FROM case_stage_history WHERE submission_id = ?", briefId);
            jdbcTemplate.update("DELETE FROM fact_finding_checks WHERE brief_id = ?", briefId);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", briefId);
        }
        createdBriefs.clear();
    }

    @Test
    void historyFailureRollsBackTheWholeCompletion() throws Exception {
        Submission brief = createBrief("CASE-FF-R1", "Grain mill");
        long version = savedVersion(saveAnswers(brief, answers("one")));
        Mockito.doThrow(new IllegalStateException("history store unavailable"))
                .when(historyRepository)
                .save(Mockito.<CaseStageHistory>any());

        mockMvc.perform(post("/cases/{id}/fact-finding/complete", brief.getId())
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("version", version))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.reference").isNotEmpty());

        Map<String, Object> check = jdbcTemplate.queryForMap(
                "SELECT result, completed_at, version FROM fact_finding_checks WHERE brief_id = ?",
                brief.getId()
        );
        assertNull(check.get("result"));
        assertNull(check.get("completed_at"));
        assertEquals(version, ((Number) check.get("version")).longValue());

        String stage = jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?",
                String.class,
                brief.getId()
        );
        assertEquals("IN_FACT_FINDING", stage);
        assertEquals(0L, historyCount(brief.getId()));
    }

    @Test
    void concurrentFirstDraftsSerializeToOneSuccessAndOneReload() throws Exception {
        Submission brief = createBrief("CASE-FF-R2", "Cold room");
        CountDownLatch bothMayStart = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Outcome> firstSave = () -> {
                bothMayStart.await(20, TimeUnit.SECONDS);
                return saveOnce(brief.getId(), answers("first"));
            };
            Callable<Outcome> secondSave = () -> {
                bothMayStart.await(20, TimeUnit.SECONDS);
                return saveOnce(brief.getId(), answers("second"));
            };
            Future<Outcome> first = pool.submit(firstSave);
            Future<Outcome> second = pool.submit(secondSave);
            bothMayStart.countDown();

            List<Outcome> outcomes = List.of(
                    first.get(60, TimeUnit.SECONDS),
                    second.get(60, TimeUnit.SECONDS)
            );

            Set<Integer> statuses = outcomes.stream()
                    .map(Outcome::status)
                    .collect(Collectors.toSet());
            assertEquals(Set.of(200, 422), statuses);

            Outcome rejected = outcomes.stream()
                    .filter(outcome -> outcome.status() == 422)
                    .findFirst()
                    .orElseThrow();
            assertTrue(rejected.body().contains(
                    "version is required when updating an existing fact-finding check."
            ));
        } finally {
            pool.shutdownNow();
        }

        Long storedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM fact_finding_checks WHERE brief_id = ?",
                Long.class,
                brief.getId()
        );
        assertEquals(1L, storedRows);
    }

    private Outcome saveOnce(UUID briefId, Map<String, Object> body) throws Exception {
        MvcResult result = mockMvc.perform(put("/cases/{id}/fact-finding", briefId)
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
        return new Outcome(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private ResultActions saveAnswers(Submission brief, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/cases/{id}/fact-finding", brief.getId())
                .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
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
        body.put("evidenceEnough", true);
        body.put("evidenceNote", "Evidence " + marker);
        body.put("relevantToHub", true);
        body.put("relevantNote", "Relevant " + marker);
        body.put("missingItems", "Missing " + marker);
        return body;
    }

    private long historyCount(UUID briefId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM case_stage_history WHERE submission_id = ?",
                Long.class,
                briefId
        );
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
        brief.transitionTo(SubmissionStage.FORWARDED, Instant.parse("2026-11-01T10:00:00Z"));
        brief.transitionTo(SubmissionStage.IN_FACT_FINDING, Instant.parse("2026-11-01T11:00:00Z"));
        Submission saved = submissions.saveAndFlush(brief);
        createdBriefs.add(saved.getId());
        return saved;
    }

    private record Outcome(int status, String body) {
    }
}
