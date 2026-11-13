package org.unipod.unipoderpbackend.dto.submission;

import java.time.Instant;
import java.util.List;
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

    public record Capabilities(
            Capability nextStep,
            Capability saveOutcome,
            Capability updateDocumentStatus
    ) {
    }

    public record TransitionOptions(List<CaseStage> allowedTransitions) {
    }
}
