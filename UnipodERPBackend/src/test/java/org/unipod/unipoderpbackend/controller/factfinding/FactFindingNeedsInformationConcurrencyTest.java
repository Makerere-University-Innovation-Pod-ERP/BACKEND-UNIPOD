package org.unipod.unipoderpbackend.controller.factfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
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
 * Committed-data concurrency for POST /cases/{id}/fact-finding/needs-information: both requests are released at the
 * same moment. Both endpoints lock the brief row first (findVisibleByIdForUpdate), so the second waits and then sees
 * the first one's result: a 409, never a 500, and never a mixed final state.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FactFindingNeedsInformationConcurrencyTest {
    private static final String OFFICER = "ni-race-officer@example.test";
    private static final String OWNER = "ni-race-owner@example.test";

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

    /** Runs after every repetition, also a failed one. */
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

    @RepeatedTest(3)
    void twoSimultaneousRequestsGiveOneOkAndOneConflict() throws Exception {
        UUID brief = briefInFactFinding();
        long version = saveDraft(brief, Map.of("missingItems", "Budget"));
        String body = needsInformationBody(version, caseVersion(brief));

        assertEquals(List.of(200, 409), concurrently(needsInformation(brief, body), needsInformation(brief, body)));

        assertEquals("IMPROVEMENT_REQUESTED", stage(brief));
        assertEquals(1, count("SELECT count(*) FROM refinement_requests WHERE brief_id = ?", brief));
        assertEquals(1, count("SELECT count(*) FROM fact_finding_checks WHERE brief_id = ? "
                + "AND result = 'NEEDS_MORE_INFORMATION' AND completed_at IS NOT NULL", brief));
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE entity_id = ? "
                + "AND action = 'submission.fact_finding_completed'", brief));
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE entity_id = ? "
                + "AND action = 'submission.refinement_requested'", brief));
        assertEquals(1, count("SELECT count(*) FROM notification_outbox WHERE aggregate_id = ? "
                + "AND event_type = 'submission.refinement_requested'", brief));
    }

    @RepeatedTest(3)
    void needsInformationRacingCompleteGivesOneWinnerAndAConsistentState() throws Exception {
        UUID brief = briefInFactFinding();
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("titleClear", true);
        answers.put("purposeClear", true);
        answers.put("evidenceEnough", false);
        answers.put("relevantToHub", true);
        answers.put("missingItems", "Budget");
        long version = saveDraft(brief, answers);
        String needsBody = needsInformationBody(version, caseVersion(brief));
        Callable<Integer> complete = () -> mockMvc.perform(post("/cases/{id}/fact-finding/complete", brief)
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andReturn().getResponse().getStatus();

        List<Integer> statuses = concurrently(needsInformation(brief, needsBody), complete);

        assertEquals(List.of(200, 409), statuses);
        Map<String, Object> check = jdbcTemplate.queryForMap(
                "SELECT result, completed_at FROM fact_finding_checks WHERE brief_id = ?", brief);
        String stage = stage(brief);
        int requests = count("SELECT count(*) FROM refinement_requests WHERE brief_id = ?", brief);
        if ("READY_FOR_REVIEW".equals(check.get("result"))) {
            assertEquals("READY_FOR_REVIEW", stage);
            assertEquals(0, requests);
            assertEquals(0, count("SELECT count(*) FROM audit_log WHERE entity_id = ? "
                    + "AND action = 'submission.refinement_requested'", brief));
        } else {
            assertEquals("NEEDS_MORE_INFORMATION", check.get("result"));
            assertEquals("IMPROVEMENT_REQUESTED", stage);
            assertEquals(1, requests);
        }
        assertTrue(check.get("completed_at") != null);
        assertEquals(1, count("SELECT count(*) FROM audit_log WHERE entity_id = ? "
                + "AND action = 'submission.fact_finding_completed'", brief));
    }

    // ---- helpers ------------------------------------------------------------------------------

    private Callable<Integer> needsInformation(UUID brief, String body) {
        return () -> mockMvc.perform(post("/cases/{id}/fact-finding/needs-information", brief)
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private String needsInformationBody(long version, long caseVersion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("caseVersion", caseVersion);
        body.put("guidanceNote", "Please add a costed budget.");
        body.put("dueDate", LocalDate.now(ZoneId.of("Africa/Kampala")).plusDays(7).toString());
        body.put("requestedChanges", List.of("Budget table"));
        return objectMapper.writeValueAsString(body);
    }

    /** Both calls start together; the statuses come back sorted. */
    private List<Integer> concurrently(Callable<Integer> one, Callable<Integer> two) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : List.of(one, two)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            return statuses.stream().sorted().toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private long saveDraft(UUID brief, Map<String, Object> answers) throws Exception {
        return objectMapper.readTree(mockMvc.perform(put("/cases/{id}/fact-finding", brief)
                        .with(user(OFFICER).roles("FACT_FINDING_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(answers)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("version").asLong();
    }

    private long caseVersion(UUID brief) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/cases/{id}", brief)
                        .with(user("coord@example.test").roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("submission").get("version").asLong();
    }

    private String stage(UUID brief) {
        return jdbcTemplate.queryForObject("SELECT stage FROM submissions WHERE id = ?", String.class, brief);
    }

    private int count(String sql, UUID brief) {
        return jdbcTemplate.queryForObject(sql, Integer.class, brief);
    }

    private UUID briefInFactFinding() {
        UUID id = transactions.execute(status -> {
            Sector sector = sectors.saveAndFlush(new Sector(UUID.randomUUID(), "Sector " + UUID.randomUUID(),
                    "sector-" + UUID.randomUUID() + "@example.test", true, Instant.parse("2026-11-01T08:00:00Z"),
                    Instant.parse("2026-11-01T08:00:00Z")));
            sectorIds.add(sector.getId());
            Submission brief = new Submission(UUID.randomUUID(), SubmissionCategory.GENERAL, "NI race brief",
                    "Purpose", "Problem", "receptionist@example.test", OWNER, null, null, null, null, OWNER, "Owner",
                    Instant.parse("2026-11-01T08:00:00Z"));
            brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
            brief.register("CASE-NR-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            brief.forward(sector, Instant.parse("2026-11-01T10:00:00Z"));
            brief.transitionTo(SubmissionStage.IN_FACT_FINDING, Instant.parse("2026-11-01T11:00:00Z"));
            return submissions.saveAndFlush(brief).getId();
        });
        briefs.add(id);
        return id;
    }
}
