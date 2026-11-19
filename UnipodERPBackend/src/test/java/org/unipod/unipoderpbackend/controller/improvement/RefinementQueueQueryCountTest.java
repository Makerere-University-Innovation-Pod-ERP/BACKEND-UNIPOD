package org.unipod.unipoderpbackend.controller.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/**
 * The refinement queue runs a fixed number of SQL statements per request, whatever the page size: the page, its
 * count (needed when the page is full) and the counters. No per-row lookups.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
class RefinementQueueQueryCountTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private RefinementRequestRepository requests;

    @Test
    void aFullPageOfTenRowsRunsThreeStatementsAndSoDoesAPageOfOne() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Kampala"));
        for (int i = 0; i < 12; i++) {
            Submission brief = submissions.saveAndFlush(ImprovementTestBriefs.ownedBy(
                    "query-count-owner@example.test", SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-QQ-"));
            requests.saveAndFlush(new RefinementRequest(brief.getId(), 1, RefinementSource.COORDINATOR,
                    "Improve " + i, today.plusDays(i), "coord@example.test", Instant.parse("2026-11-10T09:00:00Z")));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        assertEquals(3, statementsFor("10", 10, statistics));
        assertEquals(3, statementsFor("1", 1, statistics));
    }

    private long statementsFor(String size, int expectedRows, Statistics statistics) throws Exception {
        statistics.clear();
        mockMvc.perform(get("/queues/refinement").param("size", size)
                        .with(user("coord@example.test").roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.items.length()").value(expectedRows))
                .andExpect(jsonPath("$.rows.totalElements").value(12))
                .andExpect(jsonPath("$.waitingForSubmitter").value(12));
        return statistics.getPrepareStatementCount();
    }
}
