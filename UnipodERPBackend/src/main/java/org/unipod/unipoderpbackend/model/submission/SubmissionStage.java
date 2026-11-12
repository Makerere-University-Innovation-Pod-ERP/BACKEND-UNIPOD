package org.unipod.unipoderpbackend.model.submission;

/**
 * The full Waluube intake and case lifecycle.
 *
 * <p>{@code SUBMITTED} and {@code AWAITING_INFORMATION} are the submitter-facing
 * intake stages. {@code RECEIVED} is retained for the assisted/legacy
 * registration flow. {@code CONFIRMED_AS_PROJECT} and {@code REJECTED} close the
 * intake decision.</p>
 */
public enum SubmissionStage {
    DRAFT,
    SUBMITTED,
    AWAITING_INFORMATION,
    RECEIVED,
    REGISTERED,
    FACT_FINDING,
    UNDER_REVIEW,
    REFINEMENT_REQUESTED,
    FURTHER_HANDLING,
    FORWARDED,
    IN_PROGRESS,
    CONFIRMED_AS_PROJECT,
    REJECTED,
    COMPLETED
}
