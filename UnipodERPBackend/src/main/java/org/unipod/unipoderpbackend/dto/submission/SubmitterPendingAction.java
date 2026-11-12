package org.unipod.unipoderpbackend.dto.submission;

/**
 * The action a submitter has to take before a brief can move forward.
 */
public enum SubmitterPendingAction {
    CONTINUE_DRAFT,
    PROVIDE_INFORMATION,
    PROVIDE_DOCUMENT
}
