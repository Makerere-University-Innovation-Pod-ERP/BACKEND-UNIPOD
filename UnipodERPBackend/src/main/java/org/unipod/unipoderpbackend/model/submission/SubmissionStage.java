package org.unipod.unipoderpbackend.model.submission;

/**
 * The full Waluube brief lifecycle.
 *
 * <p>The values and order match the fourteen statuses in the assignment.</p>
 */
public enum SubmissionStage {
    DRAFT,
    SUBMITTED,
    AWAITING_INFORMATION,
    FORWARDED,
    IN_FACT_FINDING,
    READY_FOR_REVIEW,
    IN_REVIEW,
    IMPROVEMENT_REQUESTED,
    RESUBMITTED,
    CONFIRMED_AS_PROJECT,
    AWAITING_PAYMENT,
    IN_PROGRESS,
    COMPLETED,
    CLOSED
}
