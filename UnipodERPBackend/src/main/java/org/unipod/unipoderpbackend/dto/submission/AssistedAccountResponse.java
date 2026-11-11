package org.unipod.unipoderpbackend.dto.submission;

/**
 * The minimal submitter account created during an assisted submission.
 * {@code temporaryPassword} is returned only once, when the account is created,
 * so the receptionist can hand it to the submitter. It is never stored in
 * plaintext or returned again.
 */
public record AssistedAccountResponse(
        String email,
        boolean created,
        String temporaryPassword
) {
}
