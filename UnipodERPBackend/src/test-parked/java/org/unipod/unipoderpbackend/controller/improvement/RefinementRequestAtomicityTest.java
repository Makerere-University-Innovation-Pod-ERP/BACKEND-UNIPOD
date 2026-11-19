package org.unipod.unipoderpbackend.controller.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs without a surrounding test transaction so each request commits or rolls back for real. Each
 * test forces a failure AFTER Waluube's CaseFileService.transition has moved the brief: the stage
 * change, its history row and its audit entry must all roll back with the refinement rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RefinementRequestAtomicityTest {
    private static final String COORDINATOR = "atomic-improvement-coord@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissions;

    @MockitoSpyBean
    private RefinementRequestRepository requests;

    @MockitoSpyBean
    private AuditEntryRepository auditEntries;

    @MockitoSpyBean
    private NotificationOutbox outbox;

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
            jdbcTemplate.update("""
                    DELETE FROM refinement_requested_changes WHERE request_id IN
                        (SELECT id FROM refinement_requests WHERE brief_id = ?)
                    """, brief);
            jdbcTemplate.update("DELETE FROM case_revisions WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM refinement_requests WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM notification_outbox WHERE aggregate_id = ?", brief);
            jdbcTemplate.update("DELETE FROM case_stage_history WHERE submission_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        briefs.clear();
    }

    @Test
    void failureInTheRefinementInsertRollsBackTheStageChange() throws Exception {
        UUID brief = briefInReview();
        Map<String, Object> before = state(brief);
        Mockito.doThrow(new IllegalStateException("refinement store unavailable"))
                .when(requests).saveAndFlush(Mockito.any(RefinementRequest.class));

        expectServerError(ask(brief));

        assertEquals(before, state(brief));
    }

    @Test
    void failureInTheAuditRollsBackTheStageChange() throws Exception {
        UUID brief = briefInReview();
        Map<String, Object> before = state(brief);
        // A repository spy is an interface proxy: pass other saves to its delegating default answer
        // (callRealMethod is not possible on it), and fail only my own audit entry.
        Answer<?> real = Mockito.mockingDetails(auditEntries).getMockCreationSettings().getDefaultAnswer();
        Mockito.doAnswer(invocation -> {
            AuditEntry entry = invocation.getArgument(0);
            if ("submission.improvement_requested".equals(entry.getAction())) {
                throw new IllegalStateException("audit store unavailable");
            }
            return real.answer(invocation);
        }).when(auditEntries).save(Mockito.any(AuditEntry.class));

        expectServerError(ask(brief));

        // Waluube's own audit entry was saved first, then mine was reached and failed.
        Mockito.verify(auditEntries).save(Mockito.argThat(
                (AuditEntry entry) -> entry != null && "submission.transitioned".equals(entry.getAction())));
        Mockito.verify(auditEntries).save(Mockito.argThat(
                (AuditEntry entry) -> entry != null && "submission.improvement_requested".equals(entry.getAction())));

        assertEquals(before, state(brief));
    }

    @Test
    void failureInTheNotificationRollsBackTheStageChange() throws Exception {
        UUID brief = briefInReview();
        Map<String, Object> before = state(brief);
        Mockito.doThrow(new IllegalStateException("outbox unavailable")).when(outbox).recordSubmitterNotification(
                Mockito.any(), Mockito.anyString(), Mockito.any(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.any(Instant.class));

        expectServerError(ask(brief));

        assertEquals(before, state(brief));
    }

    private ResultActions ask(UUID brief) throws Exception {
        long version = objectMapper.readTree(mockMvc.perform(get("/cases/{id}", brief)
                        .with(user(COORDINATOR).roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("submission").get("version").asLong();
        return mockMvc.perform(post("/cases/{id}/refinement-requests", brief)
                .with(user(COORDINATOR).roles("INNOVATION_COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefinementRequestApiIntegrationTest.body(
                        "Will roll back.", LocalDate.now(ZoneId.of("Africa/Kampala")).plusDays(3),
                        List.of("One", "Two"), version))));
    }

    private static void expectServerError(ResultActions result) throws Exception {
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal server error"));
    }

    /** Stage, history, refinement rows, audit rows and outbox rows for the brief. */
    private Map<String, Object> state(UUID brief) {
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT stage FROM submissions WHERE id = ?) AS stage,
                       (SELECT version FROM submissions WHERE id = ?) AS version,
                       (SELECT count(*) FROM case_stage_history WHERE submission_id = ?) AS history,
                       (SELECT count(*) FROM refinement_requests WHERE brief_id = ?) AS requests,
                       (SELECT count(*) FROM refinement_requested_changes c
                          JOIN refinement_requests r ON r.id = c.request_id WHERE r.brief_id = ?) AS changes,
                       (SELECT count(*) FROM audit_log WHERE entity_id = ?) AS audit,
                       (SELECT count(*) FROM notification_outbox WHERE aggregate_id = ?) AS outbox
                """, brief, brief, brief, brief, brief, brief, brief);
    }

    private UUID briefInReview() {
        UUID id = transactions.execute(status ->
                submissions.save(ImprovementTestBriefs.inStage(SubmissionStage.IN_REVIEW, "CASE-IA-")).getId());
        briefs.add(id);
        assertEquals("IN_REVIEW", jdbcTemplate.queryForObject(
                "SELECT stage FROM submissions WHERE id = ?", String.class, id));
        return id;
    }
}
