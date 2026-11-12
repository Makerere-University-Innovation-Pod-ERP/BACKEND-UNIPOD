package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.ActivityItem;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.ActivityResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.Capabilities;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.Capability;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.CaseStepResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.OutcomeResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.ReviewPanelResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.StepState;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.TransitionOptions;
import org.unipod.unipoderpbackend.dto.submission.CaseStageHistoryResponse;
import org.unipod.unipoderpbackend.dto.submission.DocumentStatusUpdateRequest;
import org.unipod.unipoderpbackend.dto.submission.OutcomeUpdateRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseTransitionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.exception.storage.StorageObjectNotFoundException;
import org.unipod.unipoderpbackend.model.submission.CaseStage;
import org.unipod.unipoderpbackend.model.submission.CaseStageHistory;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.repository.submission.CaseStageHistoryRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionDocumentRepository;
import org.unipod.unipoderpbackend.repository.submission.SubmissionRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.factfinding.FactFindingGuard;

@Service
public class CaseFileService {
    private static final List<CaseStage> STEPPER_STAGES = List.of(
            CaseStage.RECEIVED,
            CaseStage.REGISTERED,
            CaseStage.FACT_FINDING,
            CaseStage.UNDER_REVIEW,
            CaseStage.REFINEMENT_REQUESTED,
            CaseStage.FURTHER_HANDLING,
            CaseStage.COMPLETED
    );
    private static final String QUORUM_REQUIRED =
            "Save stays disabled until the configured reviewer quorum is met";
    private static final String OPERATOR_REQUIRED =
            "Only an Administrator or Innovation Coordinator can change this case.";
    private static final String FACT_FINDING_REQUIRED =
            "A completed fact-finding check is required before moving this case to review.";

    private final SubmissionRepository submissions;
    private final SubmissionDocumentRepository documents;
    private final CaseStageHistoryRepository history;
    private final CaseFileEnrichmentProvider enrichment;
    private final FactFindingGuard factFindingGuard;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public CaseFileService(
            SubmissionRepository submissions,
            SubmissionDocumentRepository documents,
            CaseStageHistoryRepository history,
            CaseFileEnrichmentProvider enrichment,
            FactFindingGuard factFindingGuard,
            AuditTrail auditTrail,
            Clock clock
    ) {
        this.submissions = submissions;
        this.documents = documents;
        this.history = history;
        this.enrichment = enrichment;
        this.factFindingGuard = factFindingGuard;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CaseFileAggregateResponse get(UUID id, Authentication authentication) {
        return aggregate(visibleCase(id, authentication), authentication);
    }

    @Transactional(readOnly = true)
    public Capabilities capabilities(UUID id, Authentication authentication) {
        return capabilitiesFor(visibleCase(id, authentication), authentication);
    }

    @Transactional(readOnly = true)
    public TransitionOptions allowedTransitions(UUID id, Authentication authentication) {
        Submission submission = visibleCase(id, authentication);
        return new TransitionOptions(isOperator(authentication) ? nextStages(submission.getStage()) : List.of());
    }

    @Transactional
    public CaseFileAggregateResponse transition(
            UUID id,
            CaseTransitionRequest request,
            Authentication authentication
    ) {
        requireOperator(authentication);
        Submission submission = mutableCase(id, request.version());
        List<CaseStage> allowed = nextStages(submission.getStage());
        if (!allowed.contains(request.toStage())) {
            throw new SubmissionConflictException("The requested lifecycle transition is not allowed.");
        }
        Instant now = clock.instant();
        SubmissionStage previousStage = submission.getStage();
        SubmissionStage nextStage = SubmissionStage.valueOf(request.toStage().name());
        if (nextStage == SubmissionStage.UNDER_REVIEW
                && (previousStage == SubmissionStage.FACT_FINDING
                        || previousStage == SubmissionStage.FORWARDED)
                && !factFindingGuard.isComplete(id)) {
            throw new SubmissionConflictException(FACT_FINDING_REQUIRED);
        }
        submission.transitionTo(nextStage, now);
        history.save(new CaseStageHistory(
                UUID.randomUUID(),
                id,
                previousStage,
                nextStage,
                authentication.getName(),
                clean(request.note()),
                now
        ));
        auditTrail.record(authentication.getName(), "submission.transitioned", id,
                AuditTrail.details("stage", previousStage.name()),
                AuditTrail.details("stage", nextStage.name(), "note", clean(request.note())));
        submissions.flush();
        return aggregate(submission, authentication);
    }

    @Transactional
    public CaseFileAggregateResponse saveOutcome(
            UUID id,
            OutcomeUpdateRequest request,
            Authentication authentication
    ) {
        requireOperator(authentication);
        String decision = clean(request.decision());
        String note = clean(request.note());
        if (decision == null && note == null) {
            throw new InvalidSubmissionException("Provide an outcome decision or note.");
        }
        Submission submission = mutableCase(id, request.version());
        CaseFileEnrichmentProvider.ReviewPanel reviewPanel = enrichment.reviewPanel(id);
        if (!reviewPanel.quorumAvailable() || !reviewPanel.quorumMet()) {
            throw new SubmissionConflictException(QUORUM_REQUIRED);
        }
        submission.saveOutcome(decision, note, authentication.getName(), clock.instant());
        auditTrail.record(authentication.getName(), "submission.outcome_saved", id, null, AuditTrail.details(
                "decision", decision,
                "note", note
        ));
        submissions.flush();
        return aggregate(submission, authentication);
    }

    @Transactional
    public CaseFileAggregateResponse updateDocumentStatus(
            UUID id,
            UUID documentId,
            DocumentStatusUpdateRequest request,
            Authentication authentication
    ) {
        requireOperator(authentication);
        Submission submission = mutableCase(id, request.version());
        SubmissionDocument document = documents.findByIdAndSubmissionId(documentId, id)
                .orElseThrow(StorageObjectNotFoundException::new);
        submission.touch(clock.instant());
        document.updateStatus(request.status());
        auditTrail.record(authentication.getName(), "submission.document_status_updated", id, null, AuditTrail.details(
                "documentId", documentId,
                "name", document.getName(),
                "status", request.status().name()
        ));
        submissions.flush();
        return aggregate(submission, authentication);
    }

    private Submission visibleCase(UUID id, Authentication authentication) {
        String scope = SubmissionVisibility.scope(authentication);
        return submissions.findVisibleById(id, scope, authentication.getName())
                .orElseThrow(() -> new SubmissionNotFoundException(id));
    }

    private Submission mutableCase(UUID id, Long expectedVersion) {
        Submission submission = submissions.findByIdForUpdate(id)
                .orElseThrow(() -> new SubmissionNotFoundException(id));
        if (!Objects.equals(submission.getVersion(), expectedVersion)) {
            throw new SubmissionConflictException(
                    "This case was changed by another user. Reload it before saving."
            );
        }
        return submission;
    }

    private CaseFileAggregateResponse aggregate(Submission submission, Authentication authentication) {
        UUID id = submission.getId();
        CaseFileEnrichmentProvider.ReviewPanel review = enrichment.reviewPanel(id);
        CaseFileEnrichmentProvider.ActivityFeed activity = enrichment.activity(id);
        boolean operator = isOperator(authentication);
        List<CaseStage> allowed = operator ? nextStages(submission.getStage()) : List.of();
        Capabilities capabilities = capabilitiesFor(submission, authentication, review);
        return new CaseFileAggregateResponse(
                SubmissionResponse.from(submission),
                caseStage(submission.getStage()),
                submission.getCode(),
                documents.findAllBySubmissionIdOrderByCreatedAtAscIdAsc(id)
                        .stream().map(DocumentResponse::from).toList(),
                history.findAllBySubmissionIdOrderByChangedAtAsc(id)
                        .stream().map(CaseStageHistoryResponse::from).toList(),
                stepper(submission.getStage()),
                new OutcomeResponse(
                        submission.getOutcomeStatus(),
                        submission.getOutcomeDecision(),
                        submission.getOutcomeNote(),
                        submission.getOutcomeDecidedBy(),
                        submission.getOutcomeDecidedAt()
                ),
                capabilities,
                new TransitionOptions(allowed),
                new ReviewPanelResponse(
                        review.availability().name(),
                        review.reviewerTeam(),
                        review.ipPanel(),
                        review.quorumAvailable(),
                        review.quorumMet()
                ),
                new ActivityResponse(
                        activity.availability().name(),
                        activity.items().stream()
                                .map(item -> new ActivityItem(
                                        item.occurredAt(), item.actor(), item.action(), item.summary()
                                )).toList()
                )
        );
    }

    private Capabilities capabilitiesFor(Submission submission, Authentication authentication) {
        return capabilitiesFor(submission, authentication, enrichment.reviewPanel(submission.getId()));
    }

    private Capabilities capabilitiesFor(
            Submission submission,
            Authentication authentication,
            CaseFileEnrichmentProvider.ReviewPanel review
    ) {
        boolean operator = isOperator(authentication);
        List<CaseStage> allowed = operator ? nextStages(submission.getStage()) : List.of();
        String operatorReason = operator ? null : OPERATOR_REQUIRED;
        String quorumReason = review.quorumAvailable() && review.quorumMet() ? null : QUORUM_REQUIRED;
        boolean outcomeStageOpen = submission.getStage() != SubmissionStage.DRAFT
                && submission.getStage() != SubmissionStage.REJECTED
                && submission.getStage() != SubmissionStage.COMPLETED;
        boolean documentsOpen = submission.getStage() != SubmissionStage.REJECTED
                && submission.getStage() != SubmissionStage.COMPLETED;
        return new Capabilities(
                capability(!allowed.isEmpty(), !allowed.isEmpty()
                        ? null
                        : operator ? "There are no allowed next stages from the current stage." : operatorReason),
                capability(operator && outcomeStageOpen && quorumReason == null,
                        !operator ? operatorReason
                                : !outcomeStageOpen ? "Outcome cannot be changed in the current case stage."
                                : quorumReason),
                capability(operator && documentsOpen,
                        !operator ? operatorReason
                                : !documentsOpen ? "Closed cases cannot be changed." : null)
        );
    }

    private static List<CaseStepResponse> stepper(SubmissionStage stage) {
        CaseStage currentStage = caseStage(stage);
        int currentIndex = currentStage == null ? -1 : STEPPER_STAGES.indexOf(currentStage);
        return STEPPER_STAGES.stream()
                .map(step -> {
                    int index = STEPPER_STAGES.indexOf(step);
                    StepState state = index < currentIndex ? StepState.COMPLETE
                            : index == currentIndex ? StepState.CURRENT : StepState.UPCOMING;
                    return new CaseStepResponse(step, state);
                })
                .toList();
    }

    private static CaseStage caseStage(SubmissionStage stage) {
        return switch (stage) {
            case DRAFT -> null;
            case SUBMITTED, AWAITING_INFORMATION, RECEIVED -> CaseStage.RECEIVED;
            case REGISTERED -> CaseStage.REGISTERED;
            case FACT_FINDING, FORWARDED -> CaseStage.FACT_FINDING;
            case UNDER_REVIEW, IN_PROGRESS -> CaseStage.UNDER_REVIEW;
            case REFINEMENT_REQUESTED -> CaseStage.REFINEMENT_REQUESTED;
            case FURTHER_HANDLING, CONFIRMED_AS_PROJECT -> CaseStage.FURTHER_HANDLING;
            case REJECTED, COMPLETED -> CaseStage.COMPLETED;
        };
    }

    private static List<CaseStage> nextStages(SubmissionStage stage) {
        return switch (stage) {
            case RECEIVED -> List.of(CaseStage.REGISTERED);
            case REGISTERED -> List.of(CaseStage.FACT_FINDING);
            case FACT_FINDING, FORWARDED -> List.of(CaseStage.UNDER_REVIEW, CaseStage.REFINEMENT_REQUESTED);
            case UNDER_REVIEW, IN_PROGRESS -> List.of(CaseStage.REFINEMENT_REQUESTED);
            case REFINEMENT_REQUESTED -> List.of(CaseStage.FURTHER_HANDLING);
            case FURTHER_HANDLING -> List.of(CaseStage.COMPLETED);
            case CONFIRMED_AS_PROJECT -> List.of(CaseStage.FACT_FINDING);
            default -> List.of();
        };
    }

    private static void requireOperator(Authentication authentication) {
        if (!isOperator(authentication)) {
            throw new AccessDeniedException(OPERATOR_REQUIRED);
        }
    }

    private static boolean isOperator(Authentication authentication) {
        return hasRole(authentication, "ADMINISTRATOR", "INNOVATION_COORDINATOR");
    }

    private static boolean hasRole(Authentication authentication, String... roles) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> List.of(roles).stream()
                        .anyMatch(role -> authority.equals("ROLE_" + role)));
    }

    private static Capability capability(boolean enabled, String reason) {
        return new Capability(enabled, reason);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
