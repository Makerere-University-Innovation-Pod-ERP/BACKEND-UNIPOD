package org.unipod.unipoderpbackend.dto.improvement;

/** What a brief in the staff refinement queue is waiting for. */
public enum RefinementQueueState {
    /** An open request: the submitter has to send an improved version. */
    WAITING_FOR_SUBMITTER,
    /** The submitter answered: staff can accept the improved version. */
    READY_TO_ACCEPT,
    /**
     * Marked for improvement but no request is waiting on anyone (no recorded request, or only an answered one).
     * Main's transition, outcome and reply create requests themselves, so this appears only for older rows.
     */
    NEEDS_REQUEST
}
