package org.unipod.unipoderpbackend.service.submission;

import org.unipod.unipoderpbackend.dto.submission.CaseFileResponse;
import org.unipod.unipoderpbackend.dto.submission.CreateDocumentRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.dashboard.DashboardOverviewResponse;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.dto.reception.ReceptionQueueResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.util.CaseCodeGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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
    void forwardsDocumentsAndAdvancesCaseThroughCompletion() {
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
        DocumentResponse document = submissions.addDocument(
                draft.id(),
                new CreateDocumentRequest(
                        "Project brief",
                        DocumentStatus.SUBMITTED,
                        "file_01JCASEFILE"
                ),
                "receptionist-1"
        );
        submissions.register(draft.id(), "receptionist-2");
        var forwarded = submissions.forward(
                draft.id(),
                java.util.UUID.randomUUID(),
                "receptionist-2"
        );

        assertEquals(SubmissionStage.FORWARDED, forwarded.stage());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class,
                draft.id(),
                "submission.forwarded"
        ));

        submissions.advance(draft.id(), SubmissionStage.IN_PROGRESS, "coordinator-1");
        SubmissionResponse completed = submissions.advance(
                draft.id(),
                SubmissionStage.COMPLETED,
                "coordinator-1"
        );
        CaseFileResponse caseFile = submissions.caseFile(draft.id());

        assertEquals(SubmissionStage.COMPLETED, completed.stage());
        assertEquals(document.id(), caseFile.documents().getFirst().id());
        assertEquals(5, caseFile.stageHistory().size());
        assertEquals(SubmissionStage.COMPLETED, caseFile.stageHistory().getLast().toStage());
        assertThrows(
                SubmissionConflictException.class,
                () -> submissions.addDocument(
                        draft.id(),
                        new CreateDocumentRequest("Late document", DocumentStatus.SUBMITTED, null),
                        "coordinator-1"
                )
        );
    }

    @Test
    void receptionQueueAndOverviewExposeConsistentCountersAndPages() {
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
                "receptionist-queue-test"
        );

        ReceptionQueueResponse queue = submissions.receptionQueue(
                PageRequest.of(0, 10)
        );
        var mine = submissions.mine(
                "receptionist-queue-test",
                PageRequest.of(0, 10)
        );
        DashboardOverviewResponse overview = submissions.overview();

        assertTrue(queue.items().stream().anyMatch(item -> item.id().equals(draft.id())));
        assertTrue(queue.inQueueCount() >= 1);
        assertTrue(queue.recentlyUpdatedCount() >= 1);
        assertEquals(1, mine.getTotalElements());
        assertTrue(overview.totalSubmissions() >= 1);
        assertTrue(overview.generalSubmissions() >= 1);
        assertTrue(overview.byStage().get(SubmissionStage.DRAFT) >= 1);
    }

    @Test
    void rejectsSkippingLifecycleStages() {
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

        assertThrows(
                SubmissionConflictException.class,
                () -> submissions.advance(draft.id(), SubmissionStage.COMPLETED, "coordinator-1")
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
