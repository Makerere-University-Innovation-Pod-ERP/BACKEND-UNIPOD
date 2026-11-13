package org.unipod.unipoderpbackend.service.submission;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.reception.ReceptionQueueResponse;
import org.unipod.unipoderpbackend.dto.sector.SectorRequest;
import org.unipod.unipoderpbackend.dto.sector.SectorResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseFileResponse;
import org.unipod.unipoderpbackend.dto.submission.ConfirmProjectRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateDocumentRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckItemRequest;
import org.unipod.unipoderpbackend.dto.submission.ReceptionCheckUpdateRequest;
import org.unipod.unipoderpbackend.dto.submission.RequestInformationRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.springframework.security.access.AccessDeniedException;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.RecommendationLetterStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import org.unipod.unipoderpbackend.util.CaseCodeGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Autowired
    private SectorService sectors;

    @Autowired
    private ReceptionChecklistService checklists;

    @Test
    void createsDraftAndRegistersStudentWithCaseCodeAndHistory() {
        SubmissionResponse draft = submissions.create(
                new CreateSubmissionRequest(
                        SubmissionCategory.STUDENT,
                        "Irrigation monitor",
                        "Build a soil moisture monitor",
                        "Farmers lack soil moisture information",
                        null,
                        "2026001234",
                        "Agricultural Engineering",
                        "Dr Example",
                        RecommendationLetterStatus.SUBMITTED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "2026-01"
                ),
                "receptionist-1"
        );

        assertEquals(SubmissionStage.DRAFT, draft.stage());
        int createdYear = draft.createdAt().atZone(ZoneId.of("Africa/Kampala")).getYear();
        assertTrue(draft.code().matches("BRF-" + createdYear + "-\\d{6,}"));
        assertEquals(1, stageHistory.countBySubmissionId(draft.id()));

        SubmissionResponse registered = submissions.register(draft.id(), "receptionist-2");

        assertEquals(SubmissionStage.SUBMITTED, registered.stage());
        assertTrue(registered.code().matches("BRF-" + createdYear + "-\\d{6,}"));
        assertEquals(draft.code(), registered.code());
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
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "2026-01"
                ),
                "receptionist-1"
        );

        SubmissionResponse registered = submissions.register(draft.id(), "receptionist-2");

        assertTrue(registered.code().startsWith("BRF-"));
        assertEquals(SubmissionStage.SUBMITTED, registered.stage());
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
    void submitterCreationUsesAuthenticatedIdentityAndKeepsTrackingReference() {
        String submitter = "submitter@example.test";
        SubmissionResponse created = submissions.create(
                new CreateSubmissionRequest(
                        SubmissionCategory.GENERAL,
                        "A public innovation",
                        "Improve access",
                        "Current access is limited",
                        "spoofed-account",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "2026-01"
                ),
                submitter,
                true
        );

        assertEquals(SubmissionStage.DRAFT, created.stage());
        assertTrue(created.code().startsWith("BRF-"));
        assertEquals(submitter, created.registeredBy());
        assertEquals(submitter, created.submitterId());

        SubmissionResponse registered = submissions.register(created.id(), "receptionist-1");

        assertEquals(created.code(), registered.code());
    }

    @Test
    void requestingMoreInformationWritesHistoryAndNotificationOutboxEvent() {
        SubmissionResponse created = submissions.create(
                new CreateSubmissionRequest(
                        SubmissionCategory.GENERAL,
                        "A public innovation",
                        "Improve access",
                        "Current access is limited",
                        "submitter@example.test",
                        null,
                        null,
                        null,
                        null,
                        "submitter@example.test"
                ),
                "receptionist-1"
        );

        SubmissionResponse requested = submissions.requestInformation(
                created.id(),
                new RequestInformationRequest("Please attach a project brief."),
                new TestingAuthenticationToken("receptionist-1", "n/a", "ROLE_RECEPTIONIST")
        );

        assertEquals(SubmissionStage.DRAFT, requested.stage());
        assertEquals("Please attach a project brief.", requested.informationRequestedNote());
        assertEquals("receptionist-1", jdbcTemplate.queryForObject(
                "SELECT information_requested_by FROM submissions WHERE id = ?",
                String.class,
                created.id()
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class,
                created.id(),
                "submission.information_requested"
        ));
        assertEquals("submitter@example.test", jdbcTemplate.queryForObject(
                "SELECT payload ->> 'notificationEmail' FROM notification_outbox "
                        + "WHERE aggregate_id = ? AND event_type = 'submission.information_requested'",
                String.class,
                created.id()
        ));
        assertEquals(2, stageHistory.countBySubmissionId(created.id()));
    }

    @Test
    void staffCannotEditAnotherUsersDraft() {
        SubmissionResponse created = submissions.create(
                new CreateSubmissionRequest(
                        SubmissionCategory.GENERAL,
                        "A public innovation",
                        "Improve access",
                        "Current access is limited",
                        "submitter@example.test",
                        null,
                        null,
                        null,
                        null,
                        "submitter@example.test"
                ),
                "receptionist-1"
        );
        submissions.requestInformation(
                created.id(),
                new RequestInformationRequest("Please attach a project brief."),
                new TestingAuthenticationToken("receptionist-1", "n/a", "ROLE_RECEPTIONIST")
        );

        assertThrows(
                AccessDeniedException.class,
                () -> submissions.updateDraft(
                        created.id(),
                        new CreateSubmissionRequest(
                                SubmissionCategory.GENERAL,
                                "Updated public innovation",
                                "Improve access",
                                "Current access is limited",
                                null,
                                null,
                                null,
                                null
                        ),
                        "receptionist-2",
                        false
                )
        );

        assertEquals("Please attach a project brief.", jdbcTemplate.queryForObject(
                "SELECT information_requested_note FROM submissions WHERE id = ?",
                String.class,
                created.id()
        ));
    }

    @Test
    void informationRequestRequiresAValidSubmitterEmail() {
        SubmissionResponse created = submissions.create(
                new CreateSubmissionRequest(
                        SubmissionCategory.GENERAL,
                        "A public innovation",
                        "Improve access",
                        "Current access is limited",
                        null,
                        null,
                        null,
                        null
                ),
                "receptionist-1"
        );

        assertThrows(
                InvalidSubmissionException.class,
                () -> submissions.requestInformation(
                        created.id(),
                        new RequestInformationRequest("Please attach a project brief."),
                        new TestingAuthenticationToken("receptionist-1", "n/a", "ROLE_RECEPTIONIST")
                )
        );
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_outbox WHERE aggregate_id = ? "
                        + "AND event_type = 'submission.information_requested'",
                Integer.class,
                created.id()
        ));
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
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "2026-01"
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
        assertEquals(DocumentType.OTHER, document.documentType());
        submissions.register(draft.id(), "receptionist-2");
        SectorResponse sector = sectors.create(new SectorRequest(
                "Integration test sector",
                "sector@example.invalid",
                true
        ));
        completeReceptionChecklist(draft.id(), "receptionist-2");
        var forwarded = submissions.forward(
                draft.id(),
                sector.id(),
                "receptionist-2"
        );

        assertEquals(SubmissionStage.FORWARDED, forwarded.stage());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_outbox WHERE aggregate_id = ? AND event_type = ?",
                Integer.class,
                draft.id(),
                "submission.forwarded"
        ));
        assertEquals(
                "sector@example.invalid",
                jdbcTemplate.queryForObject(
                        "SELECT payload ->> 'notificationEmail' FROM notification_outbox "
                                + "WHERE aggregate_id = ? AND event_type = 'submission.forwarded'",
                        String.class,
                        draft.id()
                )
        );

        submissions.confirmAsProject(draft.id(), new ConfirmProjectRequest(null), "coordinator-1");
        submissions.advance(draft.id(), SubmissionStage.IN_PROGRESS, "coordinator-1");
        SubmissionResponse completed = submissions.advance(
                draft.id(),
                SubmissionStage.COMPLETED,
                "coordinator-1"
        );
        CaseFileResponse caseFile = submissions.caseFile(draft.id());

        assertEquals(SubmissionStage.COMPLETED, completed.stage());
        assertEquals(document.id(), caseFile.documents().getFirst().id());
        assertEquals(6, caseFile.stageHistory().size());
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
                        null,
                        null,
                        "queue-submitter@example.test",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "2026-01"
                ),
                "receptionist-queue-test"
        );
        submissions.submit(draft.id(), "receptionist-queue-test", false);

        ReceptionQueueResponse queue = submissions.receptionQueue(
                PageRequest.of(0, 10)
        );
        var mine = submissions.mine(
                "receptionist-queue-test",
                PageRequest.of(0, 10)
        );

        assertTrue(queue.items().stream().anyMatch(item -> item.id().equals(draft.id())));
        assertTrue(queue.inQueueCount() >= 1);
        assertTrue(queue.recentlyUpdatedCount() >= 1);
        assertEquals(1, mine.getTotalElements());
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
    void registrationRequiresAcceptedRules() {
        SubmissionResponse draft = submissions.create(generalRequest("Rules required", null), "receptionist-1");

        InvalidSubmissionException exception = assertThrows(
                InvalidSubmissionException.class,
                () -> submissions.register(draft.id(), "receptionist-2")
        );

        assertEquals("Accept the submission rules before submitting the brief.", exception.getMessage());
    }

    @Test
    void serviceRequestRegistrationRequiresPreferredDateAndPersistsIt() {
        SubmissionResponse missingDate = submissions.create(
                serviceRequest(null, null, "2026-01"),
                "receptionist-1"
        );
        assertThrows(
                InvalidSubmissionException.class,
                () -> submissions.register(missingDate.id(), "receptionist-2")
        );

        Instant preferredAt = Instant.parse("2026-12-01T09:30:00Z");
        SubmissionResponse draft = submissions.create(
                serviceRequest(preferredAt, null, "2026-01"),
                "receptionist-1"
        );
        SubmissionResponse registered = submissions.register(draft.id(), "receptionist-2");

        assertEquals(preferredAt, registered.preferredAt());
    }

    @Test
    void generalIntakeDefaultsDepartmentToInnovation() {
        SubmissionResponse draft = submissions.create(
                generalRequest("Department suggestion", "2026-01"),
                "receptionist-1"
        );

        assertEquals("Innovation", draft.department());
    }

    @Test
    void forwardFromDraftRequiresACompleteReceptionChecklist() {
        SubmissionResponse draft = submissions.create(
                generalRequest("Checklist gate", "2026-01"),
                "receptionist-1"
        );
        SectorResponse sector = sectors.create(new SectorRequest(
                "Checklist gate sector",
                "checklist-gate@example.invalid",
                true
        ));

        assertThrows(
                SubmissionConflictException.class,
                () -> submissions.forward(draft.id(), sector.id(), "receptionist-1")
        );

        completeReceptionChecklist(draft.id(), "receptionist-1");
        SubmissionResponse forwarded = submissions.forward(draft.id(), sector.id(), "receptionist-1");

        assertEquals(SubmissionStage.FORWARDED, forwarded.stage());
    }

    private static CreateSubmissionRequest generalRequest(String title, String acceptedRulesVersion) {
        return new CreateSubmissionRequest(
                SubmissionCategory.GENERAL,
                title,
                "Verify intake rules",
                "The intake rules need to be enforced",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                acceptedRulesVersion
        );
    }

    private static CreateSubmissionRequest serviceRequest(
            Instant preferredAt,
            String department,
            String acceptedRulesVersion
    ) {
        return new CreateSubmissionRequest(
                SubmissionCategory.SERVICE_REQUEST,
                "Service request",
                "Request support",
                "The lab needs support",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Prototype build",
                null,
                department,
                "A working prototype",
                preferredAt,
                acceptedRulesVersion
        );
    }

    private void completeReceptionChecklist(java.util.UUID submissionId, String actor) {
        checklists.update(
                submissionId,
                new ReceptionCheckUpdateRequest(List.of(
                        new ReceptionCheckItemRequest("DETAILS_COMPLETE", true, null),
                        new ReceptionCheckItemRequest("DOCUMENTS_COMPLETE", true, null),
                        new ReceptionCheckItemRequest("FILES_READABLE", true, null),
                        new ReceptionCheckItemRequest("CORRECT_TYPE", true, null)
                )),
                new TestingAuthenticationToken(actor, "n/a", "ROLE_RECEPTIONIST")
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void allocatesDistinctCaseCodesForConcurrentRequests() throws Exception {
        int testYear = 9999;
        int existingSequence = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM code_sequences WHERE prefix = ? AND code_year = ?",
                Integer.class,
                "BRF",
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
            assertTrue(codes.contains("BRF-9999-000001"));
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent case-code tasks did not stop in time.");
            }
            jdbcTemplate.update(
                    "DELETE FROM code_sequences WHERE prefix = ? AND code_year = ?",
                    "BRF",
                    testYear
            );
        }
    }
}
