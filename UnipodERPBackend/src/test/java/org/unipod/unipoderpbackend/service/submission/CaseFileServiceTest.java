package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.unipod.unipoderpbackend.dto.submission.CaseTransitionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentStatusUpdateRequest;
import org.unipod.unipoderpbackend.dto.submission.OutcomeUpdateRequest;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.CaseStage;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.MissingItemRepository;
import org.unipod.unipoderpbackend.repository.submission.NotificationOutbox;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.factfinding.FactFindingGuard;
import org.unipod.unipoderpbackend.service.sector.SectorService;
import org.unipod.unipoderpbackend.util.CaseCodeGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseFileServiceTest {
    private static final UUID CASE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");

    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final SubmissionDocumentRepository documents = mock(SubmissionDocumentRepository.class);
    private final CaseStageHistoryRepository history = mock(CaseStageHistoryRepository.class);
    private final MissingItemRepository missingItems = mock(MissingItemRepository.class);
    private final CaseFileEnrichmentProvider enrichment = mock(CaseFileEnrichmentProvider.class);
    private final FactFindingGuard factFindingGuard = mock(FactFindingGuard.class);
    private final NotificationOutbox notificationOutbox = mock(NotificationOutbox.class);
    private final SectorService sectorsCatalog = mock(SectorService.class);
    private final CaseCodeGenerator caseCodeGenerator = mock(CaseCodeGenerator.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final CaseFileService service = new CaseFileService(
            submissions, documents, history, missingItems, enrichment, factFindingGuard,
            notificationOutbox, sectorsCatalog, caseCodeGenerator, audit, clock
    );
    private Submission submission;

    @BeforeEach
    void setUp() {
        submission = new Submission(
                CASE_ID, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "owner", "owner", null, null, null, null, NOW.minusSeconds(60)
        );
        ReflectionTestUtils.setField(submission, "stage", SubmissionStage.IN_FACT_FINDING);
        ReflectionTestUtils.setField(submission, "version", 4L);
        when(enrichment.reviewPanel(CASE_ID)).thenReturn(
                new CaseFileEnrichmentProvider.ReviewPanel(
                        CaseFileEnrichmentProvider.Availability.NOT_CONFIGURED,
                        List.of(), false, false
                )
        );
        when(enrichment.activity(CASE_ID)).thenReturn(
                new CaseFileEnrichmentProvider.ActivityFeed(
                        CaseFileEnrichmentProvider.Availability.NOT_CONFIGURED, List.of()
                )
        );
    }

    @Test
    void coordinatorCanMoveCaseOutOfFactFindingAndHistoryKeepsNote() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(factFindingGuard.isComplete(CASE_ID)).thenReturn(true);

        var result = service.transition(
                CASE_ID,
                new CaseTransitionRequest(CaseStage.IN_REVIEW, "Checklist complete", 4L),
                coordinator
        );

        assertEquals(SubmissionStage.IN_REVIEW, result.submission().stage());
        assertEquals(CaseStage.IN_REVIEW, result.currentStage());
        assertEquals(NOW, result.submission().updatedAt());
        var savedHistory = forClass(org.unipod.unipoderpbackend.model.submission.CaseStageHistory.class);
        verify(history).save(savedHistory.capture());
        assertEquals("Checklist complete", savedHistory.getValue().getNote());
        verify(submissions).flush();
    }

    @Test
    void reviewTransitionIsBlockedUntilFactFindingIsComplete() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(factFindingGuard.isComplete(CASE_ID)).thenReturn(false);

        SubmissionConflictException exception = assertThrows(SubmissionConflictException.class,
                () -> service.transition(
                        CASE_ID,
                        new CaseTransitionRequest(CaseStage.IN_REVIEW, null, 4L),
                        coordinator
                ));

        assertEquals("A completed fact-finding check is required before moving this case to review.",
                exception.getMessage());
        assertEquals(SubmissionStage.IN_FACT_FINDING, submission.getStage());
        verify(submissions, org.mockito.Mockito.never()).flush();
    }

    @Test
    void coordinatorCanRequestRefinementDuringFactFindingWithoutACompletedCheck() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        var result = service.transition(
                CASE_ID,
                new CaseTransitionRequest(CaseStage.IMPROVEMENT_REQUESTED, "Need more detail", 4L),
                coordinator
        );

        assertEquals(SubmissionStage.IMPROVEMENT_REQUESTED, result.submission().stage());
        assertEquals(CaseStage.IMPROVEMENT_REQUESTED, result.currentStage());
        verify(factFindingGuard, org.mockito.Mockito.never()).isComplete(any());
    }

    @Test
    void factFindingOfficerCanMarkCaseReadyForReviewAfterCompletedCheck() {
        Authentication officer = authentication("officer", "ROLE_FACT_FINDING_OFFICER");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(factFindingGuard.isComplete(CASE_ID)).thenReturn(true);

        var result = service.transition(
                CASE_ID,
                new CaseTransitionRequest(CaseStage.READY_FOR_REVIEW, "Check complete", 4L),
                officer
        );

        assertEquals(SubmissionStage.READY_FOR_REVIEW, result.submission().stage());
        assertEquals(CaseStage.READY_FOR_REVIEW, result.currentStage());
        verify(history).save(any());
        verify(submissions).flush();
    }

    @Test
    void factFindingOfficerCanRequestMoreInformation() {
        Authentication officer = authentication("officer", "ROLE_FACT_FINDING_OFFICER");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        var result = service.transition(
                CASE_ID,
                new CaseTransitionRequest(CaseStage.IMPROVEMENT_REQUESTED, "Missing evidence", 4L),
                officer
        );

        assertEquals(SubmissionStage.IMPROVEMENT_REQUESTED, result.submission().stage());
        verify(factFindingGuard, org.mockito.Mockito.never()).isComplete(any());
    }

    @Test
    void factFindingOfficerCannotMoveCaseFromOtherStages() {
        Authentication officer = authentication("officer", "ROLE_FACT_FINDING_OFFICER");
        ReflectionTestUtils.setField(submission, "stage", SubmissionStage.SUBMITTED);
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        assertThrows(AccessDeniedException.class, () -> service.transition(
                CASE_ID,
                new CaseTransitionRequest(CaseStage.FORWARDED, null, 4L),
                officer
        ));
    }

    @Test
    void outcomeSaveReturnsRequiredConflictUntilConfiguredQuorumIsMet() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        SubmissionConflictException exception = assertThrows(SubmissionConflictException.class,
                () -> service.saveOutcome(
                        CASE_ID,
                        new OutcomeUpdateRequest(
                                "Needs more improvement", "Ready", null, 4L
                        ),
                        coordinator
                ));

        assertEquals("Save stays disabled until the configured reviewer quorum is met", exception.getMessage());
    }

    @Test
    void outcomeSavePersistsDecisionActorAndDateAfterQuorumMet() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(enrichment.reviewPanel(CASE_ID)).thenReturn(
                new CaseFileEnrichmentProvider.ReviewPanel(
                        CaseFileEnrichmentProvider.Availability.AVAILABLE,
                        List.of("reviewer"), true, true
                )
        );

        var result = service.saveOutcome(
                CASE_ID,
                new OutcomeUpdateRequest(
                        "Needs more improvement", "Please add field data", null, 4L
                ),
                coordinator
        );

        assertEquals(
                org.unipod.unipoderpbackend.model.submission.OutcomeStatus.DECIDED,
                result.outcome().status()
        );
        assertEquals("Needs more improvement", result.outcome().decision());
        assertEquals(SubmissionStage.IMPROVEMENT_REQUESTED, result.submission().stage());
        assertEquals("coordinator", result.outcome().decidedBy());
        assertEquals(NOW, result.outcome().decidedAt());
        verify(history).save(any());
    }

    @Test
    void outcomeSaveIsLockedAfterADecisionHasBeenSaved() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        ReflectionTestUtils.setField(
                submission,
                "outcomeStatus",
                org.unipod.unipoderpbackend.model.submission.OutcomeStatus.DECIDED
        );
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(enrichment.reviewPanel(CASE_ID)).thenReturn(
                new CaseFileEnrichmentProvider.ReviewPanel(
                        CaseFileEnrichmentProvider.Availability.AVAILABLE,
                        List.of("reviewer"), true, true
                )
        );

        SubmissionConflictException exception = assertThrows(SubmissionConflictException.class,
                () -> service.saveOutcome(
                        CASE_ID,
                        new OutcomeUpdateRequest(
                                "Needs more improvement", "Second change attempt", null, 4L
                        ),
                        coordinator
                ));

        assertEquals("The outcome cannot be changed in the current case stage.", exception.getMessage());
    }

    @Test
    void outcomeSaveRejectsUnknownDecision() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");

        assertThrows(InvalidSubmissionException.class, () -> service.saveOutcome(
                CASE_ID, new OutcomeUpdateRequest("Approve", "Ready", null, 4L), coordinator
        ));

        verify(submissions, org.mockito.Mockito.never()).findByIdForUpdate(any());
    }

    @Test
    void confirmedOutcomeAssignsProjectCodeAndDestinationDepartment() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        UUID destinationId = UUID.randomUUID();
        Sector destination = new Sector(
                destinationId, "Engineering", "eng@example.test", true,
                NOW.minusSeconds(60), NOW.minusSeconds(60)
        );
        ReflectionTestUtils.setField(submission, "submitterEmail", "owner@example.test");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(enrichment.reviewPanel(CASE_ID)).thenReturn(
                new CaseFileEnrichmentProvider.ReviewPanel(
                        CaseFileEnrichmentProvider.Availability.AVAILABLE,
                        List.of("reviewer"), true, true
                )
        );
        when(sectorsCatalog.requireActive(destinationId)).thenReturn(destination);
        when(caseCodeGenerator.nextProject(SubmissionCategory.GENERAL, 2026))
                .thenReturn("GEN-2026-000123");

        var result = service.saveOutcome(
                CASE_ID,
                new OutcomeUpdateRequest(
                        "Confirmed as project", "Strong proposal", destinationId, 4L
                ),
                coordinator
        );

        assertEquals(SubmissionStage.CONFIRMED_AS_PROJECT, result.submission().stage());
        assertEquals("GEN-2026-000123", submission.getProjectCode());
        assertEquals(NOW, submission.getForwardedAt());
        assertEquals("coordinator", submission.getForwardedBy());
        assertEquals(destinationId, result.outcome().destinationDepartmentId());
        assertEquals("Engineering", result.outcome().destinationDepartmentName());
        verify(notificationOutbox).recordForwarded(any());
        verify(notificationOutbox).recordSubmitterNotification(
                eq(CASE_ID),
                eq("submission.confirmed_as_project"),
                eq(submission.getCode()),
                eq("owner@example.test"),
                any(),
                any(),
                eq("coordinator"),
                eq(NOW)
        );
    }

    @Test
    void confirmedOutcomeCannotReturnToFactFinding() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        ReflectionTestUtils.setField(submission, "stage", SubmissionStage.CONFIRMED_AS_PROJECT);
        when(submissions.findVisibleById(CASE_ID, "ALL", "coordinator"))
                .thenReturn(Optional.of(submission));
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        assertEquals(
                List.of(CaseStage.AWAITING_PAYMENT, CaseStage.IN_PROGRESS),
                service.allowedTransitions(CASE_ID, coordinator).allowedTransitions()
        );
        SubmissionConflictException exception = assertThrows(
                SubmissionConflictException.class,
                () -> service.transition(
                        CASE_ID,
                        new CaseTransitionRequest(CaseStage.IN_FACT_FINDING, null, 4L),
                        coordinator
                )
        );
        assertEquals("The requested lifecycle transition is not allowed.", exception.getMessage());
    }

    @Test
    void confirmedOutcomeRequiresDestinationDepartment() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(enrichment.reviewPanel(CASE_ID)).thenReturn(
                new CaseFileEnrichmentProvider.ReviewPanel(
                        CaseFileEnrichmentProvider.Availability.AVAILABLE,
                        List.of("reviewer"), true, true
                )
        );

        InvalidSubmissionException exception = assertThrows(
                InvalidSubmissionException.class,
                () -> service.saveOutcome(
                        CASE_ID,
                        new OutcomeUpdateRequest(
                                "Confirmed as project", "Strong proposal", null, 4L
                        ),
                        coordinator
                )
        );

        assertEquals(
                "A destination department is required to confirm a project.",
                exception.getMessage()
        );
        assertEquals(SubmissionStage.IN_FACT_FINDING, submission.getStage());
        verify(notificationOutbox, org.mockito.Mockito.never()).recordForwarded(any());
    }

    @Test
    void closedOutcomeCompletesCaseAndNotifiesSubmitter() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        ReflectionTestUtils.setField(submission, "submitterEmail", "owner@example.test");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(enrichment.reviewPanel(CASE_ID)).thenReturn(
                new CaseFileEnrichmentProvider.ReviewPanel(
                        CaseFileEnrichmentProvider.Availability.AVAILABLE,
                        List.of("reviewer"), true, true
                )
        );

        var result = service.saveOutcome(
                CASE_ID,
                new OutcomeUpdateRequest(
                        "Closed with a reason", "Duplicate idea", null, 4L
                ),
                coordinator
        );

        assertEquals(SubmissionStage.CLOSED, result.submission().stage());
        assertEquals("Closed with a reason", result.outcome().decision());
        verify(history).save(any());
        verify(notificationOutbox).recordSubmitterNotification(
                eq(CASE_ID),
                eq("submission.closed_with_reason"),
                eq(submission.getCode()),
                eq("owner@example.test"),
                any(),
                any(),
                eq("coordinator"),
                eq(NOW)
        );
    }

    @Test
    void caseFileIncludesOpenRequestsWithDueDates() {
        Authentication reviewer = authentication("reviewer", "ROLE_REVIEWER");
        Instant dueAt = NOW.plusSeconds(3600);
        MissingItem item = MissingItem.information(
                UUID.randomUUID(),
                CASE_ID,
                "Supervisor contact",
                "Add a phone number",
                "Missing detail",
                dueAt,
                "Please update the brief",
                "officer",
                NOW.minusSeconds(30)
        );
        when(submissions.findVisibleById(CASE_ID, "REVIEWER", "reviewer"))
                .thenReturn(Optional.of(submission));
        when(missingItems.findAllBySubmissionIdAndStatusOrderByRequestedAtAscIdAsc(
                CASE_ID, MissingItemStatus.OPEN
        )).thenReturn(List.of(item));

        var result = service.get(CASE_ID, reviewer);

        assertEquals(1, result.openRequests().size());
        assertEquals(dueAt, result.openRequests().getFirst().dueAt());
    }

    @Test
    void documentStatusUpdateRequiresCurrentVersionAndChangesDocument() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        UUID documentId = UUID.randomUUID();
        SubmissionDocument document = new SubmissionDocument(
                documentId, CASE_ID, "Brief", DocumentStatus.SUBMITTED,
                "reference", "actor", NOW.minusSeconds(30)
        );
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));
        when(documents.findByIdAndSubmissionId(documentId, CASE_ID)).thenReturn(Optional.of(document));

        service.updateDocumentStatus(
                CASE_ID, documentId, new DocumentStatusUpdateRequest(DocumentStatus.VERIFIED, 4L), coordinator
        );

        assertEquals(DocumentStatus.VERIFIED, document.getStatus());
        assertEquals(NOW, submission.getUpdatedAt());
        verify(submissions).flush();
    }

    @Test
    void documentStatusUpdateRejectsStaleVersion() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        SubmissionConflictException exception = assertThrows(SubmissionConflictException.class,
                () -> service.updateDocumentStatus(
                        CASE_ID,
                        UUID.randomUUID(),
                        new DocumentStatusUpdateRequest(DocumentStatus.VERIFIED, 3L),
                        coordinator
                ));

        assertEquals("This case was changed by another user. Reload it before saving.", exception.getMessage());
        verify(documents, org.mockito.Mockito.never()).findByIdAndSubmissionId(any(), any());
        verify(submissions, org.mockito.Mockito.never()).flush();
    }

    @Test
    void staleVersionCannotTransitionCase() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        SubmissionConflictException exception = assertThrows(SubmissionConflictException.class,
                () -> service.transition(
                        CASE_ID,
                        new CaseTransitionRequest(CaseStage.IN_REVIEW, null, 3L),
                        coordinator
                ));

        assertEquals("This case was changed by another user. Reload it before saving.", exception.getMessage());
    }

    @Test
    void readOnlyRoleHasDisabledMutationCapabilitiesAndNoAllowedTransitions() {
        Authentication reviewer = authentication("reviewer", "ROLE_REVIEWER");
        when(submissions.findVisibleById(CASE_ID, "REVIEWER", "reviewer"))
                .thenReturn(Optional.of(submission));

        var capabilities = service.capabilities(CASE_ID, reviewer);

        assertFalse(capabilities.nextStep().enabled());
        assertFalse(capabilities.saveOutcome().enabled());
        assertFalse(capabilities.updateDocumentStatus().enabled());
        assertEquals(
                "Only an Administrator, Innovation Coordinator or Fact-Finding Officer can change this case.",
                capabilities.nextStep().reason()
        );
        assertEquals(List.of(), service.allowedTransitions(CASE_ID, reviewer).allowedTransitions());
        verify(submissions, times(2))
                .findVisibleById(CASE_ID, "REVIEWER", "reviewer");
    }

    @Test
    void draftOwnerCanEditOnlyTheirOwnDraft() {
        ReflectionTestUtils.setField(submission, "stage", SubmissionStage.DRAFT);
        Authentication owner = authentication("owner", "ROLE_SUBMITTER");
        when(submissions.findVisibleById(CASE_ID, "SUBMITTER", "owner"))
                .thenReturn(Optional.of(submission));

        var capabilities = service.capabilities(CASE_ID, owner);

        assertTrue(capabilities.canEditDraft());
        assertFalse(capabilities.canRespondInfo());
        assertEquals(
                "This brief is not awaiting information.",
                capabilities.lockReasons().get("canRespondInfo")
        );
        assertFalse(capabilities.canReceptionForward());
        assertEquals(
                "Only reception can forward a brief.",
                capabilities.lockReasons().get("canReceptionForward")
        );
    }

    @Test
    void receptionCanForwardASubmittedBriefButCannotEditIt() {
        ReflectionTestUtils.setField(submission, "stage", SubmissionStage.SUBMITTED);
        Authentication receptionist = authentication("desk", "ROLE_RECEPTIONIST");
        when(submissions.findVisibleById(CASE_ID, "ALL", "desk"))
                .thenReturn(Optional.of(submission));

        var capabilities = service.capabilities(CASE_ID, receptionist);

        assertTrue(capabilities.canReceptionForward());
        assertFalse(capabilities.canEditDraft());
        assertEquals(
                "This brief is no longer a draft.",
                capabilities.lockReasons().get("canEditDraft")
        );
    }

    private static Authentication authentication(String subject, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                subject, "n/a", List.of(new SimpleGrantedAuthority(role))
        );
    }
}
