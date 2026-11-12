package org.unipod.unipoderpbackend.controller.review;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewMode;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * The queue runs a fixed number of SQL statements per request (page, total, waiting count),
 * whatever the page size: no per-row lookups.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
class ReviewQueueQueryCountTest {
    private static final String REVIEWER = "query-count-reviewer@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private ReviewSettingsRepository settings;

    @Autowired
    private ReviewAssignmentRepository assignments;

    @Test
    void aPageOfTenRowsRunsThreeStatementsAndSoDoesAPageOfOne() throws Exception {
        for (int i = 0; i < 12; i++) {
            Submission brief = underReviewBrief("Counted " + i);
            settings.saveAndFlush(new ReviewSettings(brief.getId(), 1, ReviewMode.SEQUENTIAL, 1,
                    LocalDate.of(2027, 1, 1).plusDays(i), "coord@example.test"));
            assignments.saveAndFlush(new ReviewAssignment(brief.getId(), 1, REVIEWER, null, 1, "coord@example.test"));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        assertEquals(3, statementsFor("10", 10, statistics));
        assertEquals(3, statementsFor("1", 1, statistics));
    }

    private long statementsFor(String size, int expectedRows, Statistics statistics) throws Exception {
        statistics.clear();
        mockMvc.perform(get("/queues/reviews").param("size", size).with(user(REVIEWER).roles("REVIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items.length()").value(expectedRows))
                .andExpect(jsonPath("$.assignedToMe").value(12));
        return statistics.getPrepareStatementCount();
    }

    private Submission underReviewBrief(String title) {
        Submission brief = new Submission(
                UUID.randomUUID(), SubmissionCategory.GENERAL, title, "Purpose", "Problem",
                "receptionist@example.test", "submitter@example.test", null, null, null, null,
                Instant.parse("2026-11-01T08:00:00Z"));
        brief.register("CASE-QC-" + UUID.randomUUID().toString().substring(0, 8),
                Instant.parse("2026-11-01T09:00:00Z"), "receptionist@example.test");
        Instant time = Instant.parse("2026-11-01T10:00:00Z");
        brief.transitionTo(SubmissionStage.REGISTERED, time);
        brief.transitionTo(SubmissionStage.FACT_FINDING, time);
        brief.transitionTo(SubmissionStage.UNDER_REVIEW, time);
        return submissions.saveAndFlush(brief);
    }
}
