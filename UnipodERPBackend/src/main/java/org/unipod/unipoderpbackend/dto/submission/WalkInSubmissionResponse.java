package org.unipod.unipoderpbackend.dto.submission;

/**
 * The brief created for a walk-in submitter together with the account details
 * reception may need to share (a temporary password or setup link is only
 * returned when a new account was created).
 */
public record WalkInSubmissionResponse(
        SubmissionResponse submission,
        AssistedAccountResponse account
) {
}
