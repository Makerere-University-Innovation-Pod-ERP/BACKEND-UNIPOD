package org.unipod.unipoderpbackend.service.submission;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.unipod.unipoderpbackend.dto.submission.MissingItemResponse;
import org.unipod.unipoderpbackend.dto.submission.OutcomeUpdateRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseTransitionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionConflictException;
import org.unipod.unipoderpbackend.exception.submission.SubmissionNotFoundException;
import org.unipod.unipoderpbackend.exception.storage.StorageObjectNotFoundException;
import org.unipod.unipoderpbackend.model.sector.Sector;
import org.unipod.unipoderpbackend.model.submission.CaseStage;
import org.unipod.unipoderpbackend.model.submission.CaseStageHistory;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.OutcomeDecision;
import org.unipod.unipoderpbackend.model.submission.OutcomeStatus;
import org.unipod.unipoderpbackend.model.submission.Submission;
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

@Service
public class CaseFileService {
    private static final ZoneId CASE_CODE_ZONE = ZoneId.of("Africa/Kampala");
    private static final List<CaseStage> STEPPER_STAGES = List.of(
            CaseStage.SUBMITTED,
            CaseStage.FORWARDED,
            CaseStage.IN_FACT_FINDING,
            CaseStage.READY_FOR_REVIEW,
            CaseStage.IN_REVIEW,
            CaseStage.IMPROVEMENT_REQUESTED,
            CaseStage.CONFIRMED_AS_PROJECT,
            CaseStage.IN_PROGRESS,
            CaseStage.COMPLETED
    );
    private static final String QUORUM_REQUIRED =
            "Save stays disabled until the configured reviewer quorum is met";
    private static final String WRITE_REQUIRED =
            "Only an Administrator, Innovation Coordinator or Fact-Finding Officer can change this case.";
    private static final String OUTCOME_OPERATOR_REQUIRED =
            "Only an Administrator or Innovation Coordinator can save an outcome.";
    private static final String DOCUMENT_OPERATOR_REQUIRED =
            "Only an Administrator or Innovation Coordinator can change document status.";
    private static final String REVIEWER_CANNOT_ASSIGN_REASON =
            "Reviewers cannot assign reviewers to a case.";
    private static final String DRAFT_LOCKED_REASON = "This brief is no longer a draft.";
    private static final String DRAFT_OWNER_REASON = "Only the submitter can edit this draft.";
    private static final String RESPOND_INFO_STAGE_REASON = "This brief is not awaiting information.";
    private static final String RESPOND_INFO_OWNER_REASON = "Only the submitter can answer this request.";
    private static final String UPLOAD_REVISION_STAGE_REASON = "No revision has been requested for this brief.";
    private static final String UPLOAD_REVISION_OWNER_REASON = "Only the submitter can upload a revision.";
    private static final String RECEPTION_FORWARD_ROLE_REASON = "Only reception can forward a brief.";
    private static final String RECEPTION_FORWARD_STAGE_REASON = "This brief is not waiting at reception.";
    private static final String RECEPTION_CHECKLIST_INCOMPLETE_REASON =
            "Complete all four reception checks before forwarding this brief.";
    private static final String FACT_FIND_ROLE_REASON =
            "Fact-finding is only available to fact-finding officers.";
    private static final String FACT_FIND_STAGE_REASON = "This case is not in fact-finding.";
    private static final String ASSIGN_REVIEWERS_ROLE_REASON =
            "Only an Administrator or Innovation Coordinator can assign reviewers.";
    private static final String ASSIGN_REVIEWERS_STAGE_REASON =
            "Reviewers can only be assigned while the brief is under review.";
    private static final String DEPARTMENT_RESPOND_ROLE_REASON =
            "Only the receiving department can respond to this case.";
    private static final String DEPARTMENT_RESPOND_STAGE_REASON =
            "This case has not been forwarded to a department yet.";
    private static final String FACT_FINDING_REQUIRED =
            "A completed fact-finding check is required before moving this case to review.";
    private static final String REFINEMENT_NOTE_REQUIRED =
            "A note is required when requesting more information.";
    private static final List<CaseStage> FACT_FINDING_NEXT_STAGES =
            List.of(CaseStage.READY_FOR_REVIEW, CaseStage.IMPROVEMENT_REQUESTED);

    private final SubmissionRepository submissions;
    private final SubmissionDocumentRepository documents;
    private final CaseStageHistoryRepository history;
    private final MissingItemRepository missingItems;
    private final CaseFileEnrichmentProvider enrichment;
    private final FactFindingGuard factFindingGuard;
    private final ReceptionChecklistService receptionChecklists;
    private final NotificationOutbox notificationOutbox;
    private final SectorService sectorsCatalog;
    private final CaseCodeGenerator caseCodeGenerator;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public CaseFileService(
            SubmissionRepository submissions,
            SubmissionDocumentRepository documents,
            CaseStageHistoryRepository history,
            MissingItemRepository missingItems,
            CaseFileEnrichmentProvider enrichment,
            FactFindingGuard factFindingGuard,
            ReceptionChecklistService receptionChecklists,
            NotificationOutbox notificationOutbox,
            SectorService sectorsCatalog,
            CaseCodeGenerator caseCodeGenerator,
            AuditTrail auditTrail,
            Clock clock
    ) {
        this.submissions = submissions;
        this.documents = documents;
        this.history = history;
        this.missingItems = missingItems;
        this.enrichment = enrichment;
        this.factFindingGuard = factFindingGuard;
        this.receptionChecklists = receptionChecklists;
        this.notificationOutbox = notificationOutbox;
        this.sectorsCatalog = sectorsCatalog;
        this.caseCodeGenerator = caseCodeGenerator;
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
        return new TransitionOptions(allowedTransitionsFor(submission.getStage(), authentication));
    }

    @Transactional
    public CaseFileAggregateResponse transition(
            UUID id,
            CaseTransitionRequest request,
            Authentication authentication
    ) {
        Submission submission = mutableCase(id, request.version());
        if (request.toStage() == CaseStage.CONFIRMED_AS_PROJECT) {
            throw new SubmissionConflictException(
                    "Project confirmation is recorded through the outcome, not a lifecycle transition."
            );
        }
        List<CaseStage> allowed = allowedTransitionsFor(submission.getStage(), authentication);
        if (!allowed.contains(request.toStage())) {
            if (isOperator(authentication)) {
                throw new SubmissionConflictException("The requested lifecycle transition is not allowed.");
            }
            throw new AccessDeniedException(WRITE_REQUIRED);
        }
        if (request.toStage() == CaseStage.IMPROVEMENT_REQUESTED && clean(request.note()) == null) {
            throw new InvalidSubmissionException(REFINEMENT_NOTE_REQUIRED);
        }
        Instant now = clock.instant();
        SubmissionStage previousStage = submission.getStage();
        SubmissionStage nextStage = SubmissionStage.valueOf(request.toStage().name());
        if ((nextStage == SubmissionStage.READY_FOR_REVIEW
                || nextStage == SubmissionStage.IN_REVIEW)
                && (previousStage == SubmissionStage.IN_FACT_FINDING
                        || previousStage == SubmissionStage.FORWARDED
                        || previousStage == SubmissionStage.RESUBMITTED)
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
        requireOutcomeOperator(authentication);
        OutcomeDecision decision = outcomeDecision(request.decision());
        String note = clean(request.note());
        if (note == null) {
            throw new InvalidSubmissionException("An outcome note is required.");
        }
        Submission submission = mutableCase(id, request.version());
        CaseFileEnrichmentProvider.ReviewPanel reviewPanel = enrichment.reviewPanel(id);
        if (!reviewPanel.quorumAvailable() || !reviewPanel.quorumMet()) {
            throw new SubmissionConflictException(QUORUM_REQUIRED);
        }
        Instant now = clock.instant();
        String actor = authentication.getName();
        SubmissionStage previousStage = submission.getStage();
        SubmissionStage resultingStage;
        Sector destination = null;
        String projectCode = null;
        switch (decision) {
            case CONFIRMED_AS_PROJECT -> {
                if (request.destinationDepartmentId() == null) {
                    throw new InvalidSubmissionException(
                            "A destination department is required to confirm a project."
                    );
                }
                destination = sectorsCatalog.requireActive(request.destinationDepartmentId());
                projectCode = caseCodeGenerator.nextProject(
                        submission.getCategory(),
                        caseYear(now)
                );
                submission.confirmOutcomeAsProject(
                        decision.label(),
                        note,
                        actor,
                        projectCode,
                        destination,
                        now
                );
                resultingStage = submission.getStage();
            }
            case NEEDS_MORE_IMPROVEMENT -> {
                submission.requestOutcomeImprovement(decision.label(), note, actor, now);
                resultingStage = submission.getStage();
            }
            case CLOSED_WITH_REASON -> {
                submission.closeWithOutcome(decision.label(), note, actor, now);
                resultingStage = submission.getStage();
            }
            default -> throw new IllegalStateException("Unhandled outcome decision.");
        }
        if (resultingStage != previousStage) {
            history.save(new CaseStageHistory(
                    UUID.randomUUID(),
                    id,
                    previousStage,
                    resultingStage,
                    actor,
                    note,
                    now
            ));
        }
        if (decision == OutcomeDecision.CONFIRMED_AS_PROJECT) {
            notificationOutbox.recordForwarded(new NotificationOutbox.ForwardedNotification(
                    submission.getId(),
                    destination.getId(),
                    destination.getName(),
                    destination.getNotificationEmail(),
                    submission.getTitle(),
                    submission.getCode(),
                    actor,
                    now
            ));
            notifySubmitter(
                    submission,
                    "submission.confirmed_as_project",
                    "Submission " + submission.getCode() + " confirmed as a project",
                    "Your brief has been confirmed as project " + projectCode
                            + " and handed to " + destination.getName() + ".",
                    actor,
                    now
            );
        } else if (decision == OutcomeDecision.NEEDS_MORE_IMPROVEMENT) {
            notifySubmitter(
                    submission,
                    "submission.refinement_requested",
                    "More detail needed for submission " + submission.getCode(),
                    "Your brief needs more improvement: " + note,
                    actor,
                    now
            );
        } else {
            notifySubmitter(
                    submission,
                    "submission.closed_with_reason",
                    "Submission " + submission.getCode() + " closed",
                    "Your brief was closed. Reason: " + note,
                    actor,
                    now
            );
        }
        auditTrail.record(actor, "submission.outcome_saved", id, null, AuditTrail.details(
                "decision", decision.label(),
                "note", note,
                "projectCode", projectCode,
                "destinationDepartmentId", destination == null ? null : destination.getId()
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
        requireDocumentOperator(authentication);
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
        List<CaseStage> allowed = allowedTransitionsFor(submission.getStage(), authentication);
        Capabilities capabilities = capabilitiesFor(submission, authentication, review);
        return new CaseFileAggregateResponse(
                SubmissionResponse.from(submission),
                caseStage(submission.getStage()),
                submission.getCode(),
                missingItems.findAllBySubmissionIdAndStatusOrderByRequestedAtAscIdAsc(
                                id,
                                MissingItemStatus.OPEN
                        ).stream()
                        .map(MissingItemResponse::from)
                        .toList(),
                documents.findAllBySubmissionIdOrderByCreatedAtAscIdAsc(id)
                        .stream().map(DocumentResponse::from).toList(),
                history.findAllBySubmissionIdOrderByChangedAtAsc(id)
                        .stream().map(CaseStageHistoryResponse::from).toList(),
                stepper(submission.getStage()),
                new OutcomeResponse(
                        submission.getOutcomeStatus(),
                        submission.getOutcomeDecision(),
                        submission.getOutcomeNote(),
                        submission.getResponsibleSectorId(),
                        submission.getResponsibleSectorName(),
                        submission.getOutcomeDecidedBy(),
                        submission.getOutcomeDecidedAt()
                ),
                capabilities,
                new TransitionOptions(allowed),
                new ReviewPanelResponse(
                        review.availability().name(),
                        review.reviewerTeam(),
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
        boolean receptionist = hasRole(authentication, "RECEPTIONIST");
        boolean reception = operator || receptionist;
        boolean reviewer = hasRole(authentication, "REVIEWER");
        boolean factFindingOfficer = isFactFindingOfficer(authentication);
        boolean sectorOfficer = hasRole(authentication, "SECTOR_OFFICER");
        SubmissionStage stage = submission.getStage();
        boolean owner = isOwner(submission, authentication);
        List<CaseStage> allowed = allowedTransitionsFor(stage, authentication);
        String nextReason = !allowed.isEmpty()
                ? null
                : operator ? "There are no allowed next stages from the current stage."
                : factFindingOfficer ? "The fact-finding officer can only move a case that is in fact-finding."
                : WRITE_REQUIRED;
        String quorumReason = review.quorumAvailable() && review.quorumMet() ? null : QUORUM_REQUIRED;
        boolean outcomeLocked = submission.getOutcomeStatus() == OutcomeStatus.DECIDED;
        boolean outcomeStageOpen = stage != SubmissionStage.DRAFT
                && stage != SubmissionStage.CLOSED
                && stage != SubmissionStage.COMPLETED
                && stage != SubmissionStage.CONFIRMED_AS_PROJECT;
        boolean documentsOpen = stage != SubmissionStage.CLOSED
                && stage != SubmissionStage.COMPLETED;
        Capability saveOutcome = capability(
                operator && outcomeStageOpen && !outcomeLocked && quorumReason == null,
                !operator ? OUTCOME_OPERATOR_REQUIRED
                        : outcomeLocked ? "The outcome has already been saved and is locked."
                        : !outcomeStageOpen ? "Outcome cannot be changed in the current case stage."
                        : quorumReason);
        boolean canEditDraft = stage == SubmissionStage.DRAFT && (owner || reception);
        boolean canRespondInfo = stage == SubmissionStage.AWAITING_INFORMATION && owner;
        boolean canUploadRevision = stage == SubmissionStage.IMPROVEMENT_REQUESTED && owner;
        boolean receptionChecklistComplete = !reception
                || stage != SubmissionStage.SUBMITTED
                || receptionChecklists.isComplete(submission.getId());
        boolean canReceptionForward = reception
                && stage == SubmissionStage.SUBMITTED
                && receptionChecklistComplete;
        boolean canFactFind = factFindingOfficer && isFactFindingStage(stage);
        boolean canAssignReviewers = operator && stage == SubmissionStage.IN_REVIEW;
        boolean canRecordDecision = saveOutcome.enabled();
        boolean canDepartmentRespond = sectorOfficer
                && (stage == SubmissionStage.FORWARDED || stage == SubmissionStage.IN_FACT_FINDING);
        Map<String, String> lockReasons = new LinkedHashMap<>();
        if (!canEditDraft) {
            lockReasons.put(
                    "canEditDraft",
                    stage == SubmissionStage.DRAFT ? DRAFT_OWNER_REASON : DRAFT_LOCKED_REASON
            );
        }
        if (!canRespondInfo) {
            lockReasons.put(
                    "canRespondInfo",
                    stage == SubmissionStage.AWAITING_INFORMATION
                            ? RESPOND_INFO_OWNER_REASON
                            : RESPOND_INFO_STAGE_REASON
            );
        }
        if (!canUploadRevision) {
            lockReasons.put(
                    "canUploadRevision",
                    stage == SubmissionStage.IMPROVEMENT_REQUESTED
                            ? UPLOAD_REVISION_OWNER_REASON
                            : UPLOAD_REVISION_STAGE_REASON
            );
        }
        if (!canReceptionForward) {
            lockReasons.put(
                    "canReceptionForward",
                    !reception ? RECEPTION_FORWARD_ROLE_REASON
                            : stage != SubmissionStage.SUBMITTED ? RECEPTION_FORWARD_STAGE_REASON
                            : RECEPTION_CHECKLIST_INCOMPLETE_REASON
            );
        }
        if (!canFactFind) {
            lockReasons.put(
                    "canFactFind",
                    factFindingOfficer ? FACT_FIND_STAGE_REASON : FACT_FIND_ROLE_REASON
            );
        }
        if (!canAssignReviewers) {
            lockReasons.put(
                    "canAssignReviewers",
                    reviewer && !operator ? REVIEWER_CANNOT_ASSIGN_REASON
                            : !operator ? ASSIGN_REVIEWERS_ROLE_REASON
                            : ASSIGN_REVIEWERS_STAGE_REASON
            );
        }
        if (!canRecordDecision) {
            lockReasons.put("canRecordDecision", saveOutcome.reason());
        }
        if (!canDepartmentRespond) {
            lockReasons.put(
                    "canDepartmentRespond",
                    sectorOfficer ? DEPARTMENT_RESPOND_STAGE_REASON : DEPARTMENT_RESPOND_ROLE_REASON
            );
        }
        return new Capabilities(
                capability(!allowed.isEmpty(), nextReason),
                saveOutcome,
                capability(operator && documentsOpen,
                        !operator ? DOCUMENT_OPERATOR_REQUIRED
                                : !documentsOpen ? "Closed cases cannot be changed." : null),
                canEditDraft,
                canRespondInfo,
                canUploadRevision,
                canReceptionForward,
                canFactFind,
                canAssignReviewers,
                canRecordDecision,
                canDepartmentRespond,
                lockReasons
        );
    }

    private static boolean isOwner(Submission submission, Authentication authentication) {
        String submitterId = submission.getSubmitterId();
        return submitterId != null && submitterId.equalsIgnoreCase(authentication.getName());
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
            case SUBMITTED, AWAITING_INFORMATION -> CaseStage.SUBMITTED;
            case FORWARDED -> CaseStage.FORWARDED;
            case IN_FACT_FINDING -> CaseStage.IN_FACT_FINDING;
            case READY_FOR_REVIEW -> CaseStage.READY_FOR_REVIEW;
            case IN_REVIEW, RESUBMITTED -> CaseStage.IN_REVIEW;
            case IMPROVEMENT_REQUESTED -> CaseStage.IMPROVEMENT_REQUESTED;
            case CONFIRMED_AS_PROJECT, AWAITING_PAYMENT -> CaseStage.CONFIRMED_AS_PROJECT;
            case IN_PROGRESS -> CaseStage.IN_PROGRESS;
            case COMPLETED, CLOSED -> CaseStage.COMPLETED;
        };
    }

    private static List<CaseStage> nextStages(SubmissionStage stage) {
        return switch (stage) {
            case SUBMITTED -> List.of();
            case FORWARDED -> List.of(CaseStage.IN_FACT_FINDING);
            case IN_FACT_FINDING -> List.of(
                    CaseStage.READY_FOR_REVIEW,
                    CaseStage.IN_REVIEW,
                    CaseStage.IMPROVEMENT_REQUESTED
            );
            case READY_FOR_REVIEW -> List.of(
                    CaseStage.IN_REVIEW,
                    CaseStage.IMPROVEMENT_REQUESTED,
                    CaseStage.CLOSED
            );
            case IN_REVIEW -> List.of(
                    CaseStage.IMPROVEMENT_REQUESTED,
                    CaseStage.AWAITING_PAYMENT,
                    CaseStage.CLOSED
            );
            case IMPROVEMENT_REQUESTED -> List.of(CaseStage.RESUBMITTED);
            case RESUBMITTED -> List.of(CaseStage.IN_REVIEW);
            case CONFIRMED_AS_PROJECT -> List.of(CaseStage.AWAITING_PAYMENT, CaseStage.IN_PROGRESS);
            case AWAITING_PAYMENT -> List.of(CaseStage.IN_PROGRESS, CaseStage.CLOSED);
            case IN_PROGRESS -> List.of(CaseStage.COMPLETED, CaseStage.CLOSED);
            default -> List.of();
        };
    }

    private static List<CaseStage> allowedTransitionsFor(SubmissionStage stage, Authentication authentication) {
        if (isOperator(authentication)) {
            return nextStages(stage);
        }
        if (isFactFindingOfficer(authentication) && isFactFindingStage(stage)) {
            return FACT_FINDING_NEXT_STAGES;
        }
        return List.of();
    }

    private static boolean isFactFindingStage(SubmissionStage stage) {
        return stage == SubmissionStage.IN_FACT_FINDING || stage == SubmissionStage.FORWARDED;
    }

    private static void requireOutcomeOperator(Authentication authentication) {
        if (!isOperator(authentication)) {
            throw new AccessDeniedException(OUTCOME_OPERATOR_REQUIRED);
        }
    }

    private static void requireDocumentOperator(Authentication authentication) {
        if (!isOperator(authentication)) {
            throw new AccessDeniedException(DOCUMENT_OPERATOR_REQUIRED);
        }
    }

    private static boolean isOperator(Authentication authentication) {
        return hasRole(authentication, "ADMINISTRATOR", "INNOVATION_COORDINATOR");
    }

    private static boolean isFactFindingOfficer(Authentication authentication) {
        return hasRole(authentication, "FACT_FINDING_OFFICER");
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

    private static OutcomeDecision outcomeDecision(String value) {
        return OutcomeDecision.fromValue(value)
                .orElseThrow(() -> new InvalidSubmissionException(
                        "Choose Confirmed as project, Needs more improvement, or Closed with a reason."
                ));
    }

    private void notifySubmitter(
            Submission submission,
            String eventType,
            String subject,
            String message,
            String actor,
            Instant occurredAt
    ) {
        String recipient = SubmissionService.isEmailAddress(submission.getSubmitterEmail())
                ? submission.getSubmitterEmail()
                : SubmissionService.isEmailAddress(submission.getSubmitterId())
                        ? submission.getSubmitterId()
                        : null;
        if (recipient == null) {
            return;
        }
        notificationOutbox.recordSubmitterNotification(
                submission.getId(),
                eventType,
                submission.getCode(),
                recipient,
                subject,
                message,
                actor,
                occurredAt
        );
    }

    private static int caseYear(Instant now) {
        return LocalDate.ofInstant(now, CASE_CODE_ZONE).getYear();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
