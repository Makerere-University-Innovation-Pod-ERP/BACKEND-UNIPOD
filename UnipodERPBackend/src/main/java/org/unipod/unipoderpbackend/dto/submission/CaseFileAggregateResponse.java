package org.unipod.unipoderpbackend.dto.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.submission.CaseStage;
import org.unipod.unipoderpbackend.model.submission.OutcomeStatus;

public record CaseFileAggregateResponse(
        SubmissionResponse submission,
        CaseStage currentStage,
        String reference,
        List<MissingItemResponse> openRequests,
        List<DocumentResponse> documents,
        List<CaseStageHistoryResponse> stageHistory,
        List<CaseStepResponse> stepper,
        OutcomeResponse outcome,
        Capabilities capabilities,
        TransitionOptions transitionOptions,
        ReviewPanelResponse reviewPanel,
        ActivityResponse activity
) {
    public record CaseStepResponse(CaseStage stage, StepState state) {
    }

    public enum StepState {
        UPCOMING,
        CURRENT,
        COMPLETE
    }

    public record OutcomeResponse(
            OutcomeStatus status,
            String decision,
            String note,
            UUID destinationDepartmentId,
            String destinationDepartmentName,
            String decidedBy,
            Instant decidedAt
    ) {
    }

    public record ReviewPanelResponse(
            String availability,
            List<String> reviewerTeam,
            boolean quorumAvailable,
            boolean quorumMet
    ) {
    }

    public record ActivityResponse(String availability, List<ActivityItem> items) {
    }

    public record ActivityItem(Instant occurredAt, String actor, String action, String summary) {
    }

    public record Capability(boolean enabled, String reason) {
    }

    /**
     * Server-derived permissions for the case file. The first three fields are
     * the original shape and remain in place for existing consumers. The
     * remaining booleans mirror the frontend permission helpers; whenever a
     * boolean is false its camelCase name appears in {@code lock_reasons} with
     * the reason the action is unavailable.
     */
    public record Capabilities(
            Capability nextStep,
            Capability saveOutcome,
            Capability updateDocumentStatus,
            boolean canEditDraft,
            boolean canRespondInfo,
            boolean canUploadRevision,
            boolean canReceptionForward,
            boolean canFactFind,
            boolean canAssignReviewers,
            boolean canRecordDecision,
            boolean canDepartmentRespond,
            @JsonProperty("lock_reasons") Map<String, String> lockReasons
    ) {
    }

    public record TransitionOptions(List<CaseStage> allowedTransitions) {
    }
}
