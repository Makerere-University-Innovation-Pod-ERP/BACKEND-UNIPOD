package org.unipod.unipoderpbackend.controller.improvement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
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
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequestedChange;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/** The refinement history runs the same small number of SQL statements for 1 or 5 requests. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
class RefinementHistoryQueryCountTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SubmissionRepository submissions;

    @Autowired
    private RefinementRequestRepository requests;

    @Autowired
    private RefinementRequestedChangeRepository changes;

    @Test
    void statementCountDoesNotGrowWithRequestsOrChanges() throws Exception {
        UUID small = briefWith(1, 1);
        UUID large = briefWith(5, 5);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long forOne = statementsFor(small, 1, 1, statistics);
        long forFive = statementsFor(large, 5, 5, statistics);

        assertEquals(forOne, forFive, "statement count must not grow with requests or changes");
        assertTrue(forFive <= 4, "expected a small fixed number of statements, got " + forFive);
    }

    private long statementsFor(UUID brief, int requestCount, int changeCount, Statistics statistics) throws Exception {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        mockMvc.perform(get("/cases/{id}/refinement-history", brief)
                        .with(user("coord@example.test").roles("INNOVATION_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.length()").value(requestCount))
                .andExpect(jsonPath("$.requests[" + (requestCount - 1) + "].requestedChanges.length()")
                        .value(changeCount));
        return statistics.getPrepareStatementCount();
    }

    private UUID briefWith(int requestCount, int changeCount) {
        UUID id = submissions.saveAndFlush(ImprovementTestBriefs.inStage(SubmissionStage.IN_REVIEW, "CASE-IQ-")).getId();
        for (int cycle = 1; cycle <= requestCount; cycle++) {
            RefinementRequest request = requests.saveAndFlush(new RefinementRequest(id, cycle,
                    RefinementSource.COORDINATOR, "Guidance " + cycle, LocalDate.of(2027, 1, cycle),
                    "coord@example.test", Instant.parse("2026-11-12T09:00:00Z")));
            for (int position = 1; position <= changeCount; position++) {
                changes.saveAndFlush(new RefinementRequestedChange(request.getId(), position, "Change " + position));
            }
        }
        return id;
    }
}
