package org.unipod.unipoderpbackend.submissions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@Transactional
class SubmissionRegistrationIntegrationTest {
    @Autowired
    private SubmissionService submissions;

    @Autowired
    private CaseStageHistoryRepository stageHistory;

    @Autowired
    private CaseCodeGenerator caseCodeGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsDraftAndRegistersStudentWithCaseCodeAndHistory() {
        SubmissionResponse draft = submissions.create(
                new CreateSubmissionRequest(
                        SubmissionCategory.STUDENT,
                        "Irrigation monitor",
                        "Build a soil moisture monitor",
                        "Farmers lack soil moisture information",
                        "2026001234",
                        "Agricultural Engineering",
                        "Dr Example",
                        RecommendationLetterStatus.SUBMITTED
                ),
                "receptionist-1"
        );

        assertEquals(SubmissionStage.DRAFT, draft.stage());
        assertNull(draft.code());
        assertEquals(1, stageHistory.countBySubmissionId(draft.id()));

        SubmissionResponse registered = submissions.register(draft.id(), "receptionist-2");

        assertEquals(SubmissionStage.RECEIVED, registered.stage());
        int receivedYear = registered.receivedAt().atZone(ZoneId.of("Africa/Kampala")).getYear();
        assertTrue(registered.code().matches("STU-" + receivedYear + "-\\d{6,}"));
        assertEquals("receptionist-1", registered.registeredBy());
        assertEquals("receptionist-2", registered.receivingOfficer());
        assertEquals(2, stageHistory.countBySubmissionId(draft.id()));
    }

    @Test
    void rejectsStudentRegistrationWithoutRequiredDetails() {
        SubmissionResponse draft = submissions.create(
                new CreateSubmissionRequest(
                        SubmissionCategory.STUDENT,
                        "Incomplete student case",
                        "Prototype a solution",
                        "Problem statement",
                        null,
                        null,
                        null,
                        null
                ),
                "receptionist-1"
        );

        assertThrows(
                InvalidSubmissionException.class,
                () -> submissions.register(draft.id(), "receptionist-2")
        );
    }

    @Test
    void registersGeneralSubmissionWithoutStudentDetails() {
        SubmissionResponse draft = submissions.create(
                new CreateSubmissionRequest(
                        SubmissionCategory.GENERAL,
                        "Community charging point",
                        "Explore a shared charging point",
                        "Reliable charging is not readily available",
                        null,
                        null,
                        null,
                        null
                ),
                "receptionist-1"
        );

        SubmissionResponse registered = submissions.register(draft.id(), "receptionist-2");

        assertTrue(registered.code().startsWith("GEN-"));
        assertEquals(SubmissionStage.RECEIVED, registered.stage());
    }

    @Test
    void rejectsStudentFieldsForGeneralSubmission() {
        assertThrows(
                InvalidSubmissionException.class,
                () -> submissions.create(
                        new CreateSubmissionRequest(
                                SubmissionCategory.GENERAL,
                                "Community charging point",
                                "Explore a shared charging point",
                                "Reliable charging is not readily available",
                                "2026001234",
                                null,
                                null,
                                null
                        ),
                        "receptionist-1"
                )
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void allocatesDistinctCaseCodesForConcurrentRequests() throws Exception {
        int testYear = 9999;
        int existingSequence = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM code_sequences WHERE prefix = ? AND code_year = ?",
                Integer.class,
                "GEN",
                testYear
        );
        assumeTrue(existingSequence == 0, "Reserved integration-test sequence is already in use.");

        int requestCount = 8;
        var executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<String>> results = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        startGate.await();
                        return caseCodeGenerator.next(SubmissionCategory.GENERAL, testYear);
                    }))
                    .toList();
            startGate.countDown();

            var codes = new HashSet<String>();
            for (var result : results) {
                codes.add(result.get(10, TimeUnit.SECONDS));
            }

            assertEquals(requestCount, codes.size());
            assertTrue(codes.contains("GEN-9999-000001"));
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent case-code tasks did not stop in time.");
            }
            jdbcTemplate.update(
                    "DELETE FROM code_sequences WHERE prefix = ? AND code_year = ?",
                    "GEN",
                    testYear
            );
        }
    }
}
