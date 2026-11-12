package org.unipod.unipoderpbackend.model.submission;

/**
 * Document state. {@code REQUIRED}, {@code OPTIONAL} and {@code MISSING} are the
 * intake states; the remaining values are retained for the case-file workflow.
 */
public enum DocumentStatus {
    REQUIRED,
    OPTIONAL,
    MISSING,
    VERIFIED,
    SUBMITTED,
    ONE_FILE,
    NOT_REQUIRED
}
