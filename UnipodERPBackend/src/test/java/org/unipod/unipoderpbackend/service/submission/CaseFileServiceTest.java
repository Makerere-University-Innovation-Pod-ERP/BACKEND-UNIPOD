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
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.model.submission.CaseStage;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.factfinding.FactFindingGuard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
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
    private final CaseFileEnrichmentProvider enrichment = mock(CaseFileEnrichmentProvider.class);
    private final FactFindingGuard factFindingGuard = mock(FactFindingGuard.class);
    private final AuditTrail audit = mock(AuditTrail.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final CaseFileService service = new CaseFileService(
            submissions, documents, history, enrichment, factFindingGuard, audit, clock
    );
    private Submission submission;

    @BeforeEach
    void setUp() {
        submission = new Submission(
                CASE_ID, SubmissionCategory.GENERAL, "Title", "Purpose", "Problem",
                "owner", "owner", null, null, null, null, NOW.minusSeconds(60)
        );
        ReflectionTestUtils.setField(submission, "stage", SubmissionStage.FACT_FINDING);
        ReflectionTestUtils.setField(submission, "version", 4L);
        when(enrichment.reviewPanel(CASE_ID)).thenReturn(
                new CaseFileEnrichmentProvider.ReviewPanel(
                        CaseFileEnrichmentProvider.Availability.NOT_CONFIGURED,
                        List.of(), List.of(), false, false
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
                new CaseTransitionRequest(CaseStage.UNDER_REVIEW, "Checklist complete", 4L),
                coordinator
        );

        assertEquals(SubmissionStage.UNDER_REVIEW, result.submission().stage());
        assertEquals(CaseStage.UNDER_REVIEW, result.currentStage());
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
                        new CaseTransitionRequest(CaseStage.UNDER_REVIEW, null, 4L),
                        coordinator
                ));

        assertEquals("A completed fact-finding check is required before moving this case to review.",
                exception.getMessage());
        assertEquals(SubmissionStage.FACT_FINDING, submission.getStage());
        verify(submissions, org.mockito.Mockito.never()).flush();
    }

    @Test
    void coordinatorCanRequestRefinementDuringFactFindingWithoutACompletedCheck() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        var result = service.transition(
                CASE_ID,
                new CaseTransitionRequest(CaseStage.REFINEMENT_REQUESTED, "Need more detail", 4L),
                coordinator
        );

        assertEquals(SubmissionStage.REFINEMENT_REQUESTED, result.submission().stage());
        assertEquals(CaseStage.REFINEMENT_REQUESTED, result.currentStage());
        verify(factFindingGuard, org.mockito.Mockito.never()).isComplete(any());
    }

    @Test
    void factFindingOfficerCannotMoveCaseOutOfFactFinding() {
        Authentication officer = authentication("officer", "ROLE_FACT_FINDING_OFFICER");

        assertThrows(AccessDeniedException.class, () -> service.transition(
                CASE_ID,
                new CaseTransitionRequest(CaseStage.UNDER_REVIEW, null, 4L),
                officer
        ));
    }

    @Test
    void outcomeSaveReturnsRequiredConflictUntilConfiguredQuorumIsMet() {
        Authentication coordinator = authentication("coordinator", "ROLE_INNOVATION_COORDINATOR");
        when(submissions.findByIdForUpdate(CASE_ID)).thenReturn(Optional.of(submission));

        SubmissionConflictException exception = assertThrows(SubmissionConflictException.class,
                () -> service.saveOutcome(
                        CASE_ID, new OutcomeUpdateRequest("Approve", "Ready", 4L), coordinator
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
                        List.of("reviewer"), List.of(), true, true
                )
        );

        var result = service.saveOutcome(
                CASE_ID, new OutcomeUpdateRequest("Approve", "Approved by panel", 4L), coordinator
        );

        assertEquals(org.unipod.unipoderpbackend.model.submission.OutcomeStatus.DECIDED, result.outcome().status());
        assertEquals("Approve", result.outcome().decision());
        assertEquals("coordinator", result.outcome().decidedBy());
        assertEquals(NOW, result.outcome().decidedAt());
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
                        new CaseTransitionRequest(CaseStage.UNDER_REVIEW, null, 3L),
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
        assertEquals("Only an Administrator or Innovation Coordinator can change this case.",
                capabilities.nextStep().reason());
        assertEquals(List.of(), service.allowedTransitions(CASE_ID, reviewer).allowedTransitions());
        verify(submissions, times(2))
                .findVisibleById(CASE_ID, "REVIEWER", "reviewer");
    }

    private static Authentication authentication(String subject, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                subject, "n/a", List.of(new SimpleGrantedAuthority(role))
        );
    }
}
