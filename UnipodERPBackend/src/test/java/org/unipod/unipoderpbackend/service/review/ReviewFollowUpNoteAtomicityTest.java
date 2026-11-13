package org.unipod.unipoderpbackend.service.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.unipod.unipoderpbackend.model.audit.AuditEntry;
import org.unipod.unipoderpbackend.model.review.Review;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewDecision;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.audit.AuditEntryRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * Runs without a surrounding test transaction so each request commits or rolls back for real:
 * the note and its audit row share one transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewFollowUpNoteAtomicityTest {
    private static final String COORDINATOR = "atomic-notes-coord@example.test";

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private ReviewAudit reviewAudit;

    @MockitoSpyBean
    private AuditEntryRepository auditEntries;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactions;

    private final List<UUID> briefs = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (UUID brief : briefs) {
            jdbcTemplate.update("""
                    DELETE FROM review_follow_up_notes WHERE review_id IN (
                        SELECT r.id FROM reviews r JOIN review_assignments a ON a.id = r.assignment_id
                        WHERE a.brief_id = ?)
                    """, brief);
            jdbcTemplate.update("""
                    DELETE FROM reviews WHERE assignment_id IN
                        (SELECT id FROM review_assignments WHERE brief_id = ?)
                    """, brief);
            jdbcTemplate.update("DELETE FROM review_assignments WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM review_settings WHERE brief_id = ?", brief);
            jdbcTemplate.update("DELETE FROM submissions WHERE id = ?", brief);
        }
        briefs.clear();
    }

    @Test
    void failureAfterTheNoteAndTheAuditCallRollsBackBoth() throws Exception {
        String reviewer = "atomic-notes-" + UUID.randomUUID() + "@example.test";
        UUID brief = briefWithSubmittedReview(reviewer);
        Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("failure after the audit call");
        }).when(reviewAudit).followUpAdded(Mockito.anyString(), Mockito.any(ReviewAssignment.class));

        expectServerError(addNote(brief, reviewer));

        Mockito.verify(reviewAudit).followUpAdded(Mockito.anyString(), Mockito.any(ReviewAssignment.class));
        Mockito.verify(auditEntries).save(Mockito.any(AuditEntry.class));
        assertEquals(0, notes(brief));
        assertEquals(0, auditRows(brief));
    }

    @Test
    void failingAuditInsertRollsBackTheNote() throws Exception {
        String reviewer = "atomic-notes-" + UUID.randomUUID() + "@example.test";
        UUID brief = briefWithSubmittedReview(reviewer);
        Mockito.doThrow(new IllegalStateException("audit store unavailable"))
                .when(auditEntries).save(Mockito.any(AuditEntry.class));

        expectServerError(addNote(brief, reviewer));

        assertEquals(0, notes(brief));
        assertEquals(0, auditRows(brief));
    }

    private ResultActions addNote(UUID brief, String reviewer) throws Exception {
        return mockMvc.perform(post("/cases/{id}/my-review/follow-up-notes", brief)
                .with(user(reviewer).roles("REVIEWER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Will roll back.\"}"));
    }

    private static void expectServerError(ResultActions result) throws Exception {
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal server error"))
                .andExpect(jsonPath("$.reference").isNotEmpty());
    }

    private int notes(UUID brief) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM review_follow_up_notes n
                JOIN reviews r ON r.id = n.review_id JOIN review_assignments a ON a.id = r.assignment_id
                WHERE a.brief_id = ?
                """, Integer.class, brief);
    }

    private int auditRows(UUID brief) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'submission.review_follow_up_added'",
                Integer.class, brief);
    }

    private UUID briefWithSubmittedReview(String reviewer) {
        UUID id = transactions.execute(status -> {
            Submission brief = new Submission(
                    UUID.randomUUID(), SubmissionCategory.GENERAL, "Atomic notes brief", "Purpose", "Problem",
                    "receptionist@example.test", "submitter@example.test", null, null, null, null,
                    Instant.parse("2026-11-01T08:00:00Z"));
            brief.applyIntakeDetails(null, null, null, null, null, null, "2026-01");
            brief.register("CASE-NA-" + UUID.randomUUID().toString().substring(0, 8),
                    Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
            Instant time = Instant.parse("2026-11-01T10:00:00Z");
            brief.transitionTo(SubmissionStage.FORWARDED, time);
            brief.transitionTo(SubmissionStage.IN_FACT_FINDING, time);
            brief.transitionTo(SubmissionStage.IN_REVIEW, time);
            Submission saved = submissions.save(brief);
            settings.save(new ReviewSettings(saved.getId(), 1, ReviewMode.PARALLEL, 1, null, COORDINATOR));
            ReviewAssignment assignment = new ReviewAssignment(saved.getId(), 1, reviewer, null, 1, COORDINATOR);
            assignment.setStatus(ReviewAssignmentStatus.SUBMITTED);
            ReviewAssignment stored = assignments.saveAndFlush(assignment);
            reviews.save(new Review(stored.getId(), ReviewDecision.RECOMMENDED, "Done.", Instant.now()));
            return saved.getId();
        });
        briefs.add(id);
        return id;
    }
}
