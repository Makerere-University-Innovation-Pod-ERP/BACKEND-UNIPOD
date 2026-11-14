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
import org.unipod.unipoderpbackend.model.improvement.CaseRevision;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequestedChange;
import org.unipod.unipoderpbackend.model.improvement.RefinementSource;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.improvement.CaseRevisionRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestRepository;
import org.unipod.unipoderpbackend.repository.improvement.RefinementRequestedChangeRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;

/** The submitter's view runs the same small number of SQL statements for 1 cycle and for 5 cycles. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
class MyImprovementQueryCountTest {
    private static final String OWNER = "count-owner@example.test";

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

    @Autowired
    private CaseRevisionRepository revisions;

    @Test
    void statementCountDoesNotGrowWithCycles() throws Exception {
        UUID one = briefWithCycles(1);
        UUID five = briefWithCycles(5);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        long forOne = statementsFor(one, 0, statistics);
        long forFive = statementsFor(five, 4, statistics);

        assertEquals(forOne, forFive, "statement count must not grow with the number of cycles");
        assertTrue(forFive <= 4, "expected a small fixed number of statements, got " + forFive);
    }

    private long statementsFor(UUID brief, int previous, Statistics statistics) throws Exception {
        entityManager.flush();
        entityManager.clear();
        statistics.clear();
        mockMvc.perform(get("/cases/{id}/my-improvement", brief).with(user(OWNER).roles("SUBMITTER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openRequest.requestedChanges.length()").value(3))
                .andExpect(jsonPath("$.previousRequests.length()").value(previous));
        return statistics.getPrepareStatementCount();
    }

    /** {@code cycles - 1} answered cycles, each with a revision, then one open cycle; three changes each. */
    private UUID briefWithCycles(int cycles) {
        UUID id = submissions.saveAndFlush(ImprovementTestBriefs.ownedBy(
                OWNER, SubmissionStage.IMPROVEMENT_REQUESTED, "CASE-MQ-")).getId();
        for (int cycle = 1; cycle <= cycles; cycle++) {
            RefinementRequest request = requests.saveAndFlush(new RefinementRequest(id, cycle,
                    RefinementSource.COORDINATOR, "Guidance " + cycle, LocalDate.of(2027, 1, cycle),
                    "coord@example.test", Instant.parse("2026-11-12T09:00:00Z")));
            for (int position = 1; position <= 3; position++) {
                changes.saveAndFlush(new RefinementRequestedChange(request.getId(), position, "Change " + position));
            }
            if (cycle < cycles) {
                request.setStatus(RefinementStatus.RESPONDED);
                request.setRespondedAt(Instant.parse("2026-11-13T09:00:00Z"));
                requests.saveAndFlush(request);
                revisions.saveAndFlush(new CaseRevision(id, cycle + 1, request.getId(), "Answer " + cycle, OWNER,
                        Instant.parse("2026-11-13T09:00:00Z")));
            }
        }
        return id;
    }
}
