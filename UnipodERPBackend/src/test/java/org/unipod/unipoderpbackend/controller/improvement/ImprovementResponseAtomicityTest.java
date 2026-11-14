package org.unipod.unipoderpbackend.controller.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.submission.SubmissionService;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs without a surrounding test transaction so each request commits or rolls back for real. Each test
 * forces a failure AFTER Waluube's SubmissionService.reply has moved the brief to RESUBMITTED: the stage,
 * brief_version, history row, his audit entry and his notifications must all roll back with my rows.
 *
 * <p>Its spy set needs its own Spring context; it is closed after this class so its connection pool does not
 * stay open for the rest of the suite (the test database allows 100 connections).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class ImprovementResponseAtomicityTest {
    private static final String OWNER = "atomic-answer-owner@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @MockitoSpyBean
    private SubmissionService submissionService;

    @MockitoSpyBean
    private CaseRevisionRepository revisions;

    @MockitoSpyBean
    private RefinementRequestRepository requests;

    @MockitoSpyBean
    private AuditEntryRepository auditEntries;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<UUID> briefs = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            jdbcTemplate.update("DELETE FROM case_revisions WHERE brief_id = ?", brief);
            jdbcTemplate.update("""
                    DELETE FROM refinement_requested_changes WHERE request_id IN
                        (SELECT id FROM refinement_requests WHERE brief_id = ?)
                    """, brief);
            jdbcTemplate.update("DELETE FROM refinement_requests WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM notification_outbox WHERE aggregate_id = ?", brief);
            jdbcTemplate.update("DELETE FROM case_stage_history WHERE submission_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        briefs.clear();
    }

    @Test
    void failureInTheRevisionInsertRollsBackWaluubesReply() throws Exception {
        UUID brief = briefWithOpenRequest();
        Map<String, Object> before = state(brief);
        Mockito.doThrow(new IllegalStateException("revision store unavailable"))
                .when(revisions).saveAndFlush(Mockito.any(CaseRevision.class));

        expectServerError(answer(brief));

        verifyReplyRan();
        assertEquals(before, state(brief));
    }

    @Test
    void failureInTheRequestUpdateRollsBackWaluubesReply() throws Exception {
        UUID brief = briefWithOpenRequest();
        Map<String, Object> before = state(brief);
        Mockito.doThrow(new IllegalStateException("request store unavailable"))
                .when(requests).saveAndFlush(Mockito.any(RefinementRequest.class));

        expectServerError(answer(brief));

        verifyReplyRan();
        assertEquals(before, state(brief));
    }

    @Test
    void failureInMyAuditRollsBackWaluubesReply() throws Exception {
        UUID brief = briefWithOpenRequest();
        Map<String, Object> before = state(brief);
        // A repository spy is an interface proxy: pass other saves to its delegating default answer
        // (callRealMethod is not possible on it), and fail only my own audit entry.
        Answer<?> real = Mockito.mockingDetails(auditEntries).getMockCreationSettings().getDefaultAnswer();
        Mockito.doAnswer(invocation -> {
            AuditEntry entry = invocation.getArgument(0);
            if ("submission.improved_version_submitted".equals(entry.getAction())) {
                throw new IllegalStateException("audit store unavailable");
            }
            return real.answer(invocation);
        }).when(auditEntries).save(Mockito.any(AuditEntry.class));

        expectServerError(answer(brief));

        // Waluube's own audit entry was saved first, then mine was reached and failed.
        Mockito.verify(auditEntries).save(Mockito.argThat(
                (AuditEntry entry) -> entry != null && "submission.replied".equals(entry.getAction())));
        Mockito.verify(auditEntries).save(Mockito.argThat(
                (AuditEntry entry) -> entry != null && "submission.improved_version_submitted".equals(entry.getAction())));

        verifyReplyRan();
        assertEquals(before, state(brief));
    }

    private void verifyReplyRan() {
        Mockito.verify(submissionService).reply(Mockito.any(), Mockito.any(), Mockito.eq(OWNER));
    }

    private ResultActions answer(UUID brief) throws Exception {
        return mockMvc.perform(post("/cases/{id}/improved-version", brief)
                .with(user(OWNER).roles("SUBMITTER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "whatChanged", "Will roll back.", "requestVersion", 0, "title", "Rolled back title"))));
    }

    private static void expectServerError(ResultActions result) throws Exception {
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal server error"));
    }

    /** Everything reply or my code could change for this brief. */
    private Map<String, Object> state(UUID brief) {
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT stage FROM submissions WHERE id = ?) AS stage,
                       (SELECT brief_version FROM submissions WHERE id = ?) AS brief_version,
                       (SELECT title FROM submissions WHERE id = ?) AS title,
                       (SELECT version FROM submissions WHERE id = ?) AS version,
                       (SELECT count(*) FROM case_stage_history WHERE submission_id = ?) AS history,
                       (SELECT count(*) FROM case_revisions WHERE brief_id = ?) AS revisions,
                       (SELECT status FROM refinement_requests WHERE brief_id = ?) AS request_status,
                       (SELECT version FROM refinement_requests WHERE brief_id = ?) AS request_version,
                       (SELECT count(*) FROM audit_log WHERE entity_id = ?) AS audit,
                       (SELECT count(*) FROM notification_outbox WHERE aggregate_id = ?) AS outbox
                """, brief, brief, brief, brief, brief, brief, brief, brief, brief, brief);
    }

    /** A brief in IMPROVEMENT_REQUESTED with an OPEN request, seeded directly (no audit or outbox rows). */
    private UUID briefWithOpenRequest() {
        UUID id = transactions.execute(status -> {
            UUID briefId = submissions.save(ImprovementTestBriefs.ownedBy(
                    OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-AV-")).getId();
            requests.save(new RefinementRequest(briefId, 1, RefinementSource.COORDINATOR, "Please improve.",
                    LocalDate.of(2027, 1, 1), "coord@example.test", Instant.parse("2026-11-12T09:00:00Z")));
            return briefId;
        });
        briefs.add(id);
        assertEquals("IMPROVEMENT_REQUESTED", jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?", String.class, id));
        return id;
    }
}
