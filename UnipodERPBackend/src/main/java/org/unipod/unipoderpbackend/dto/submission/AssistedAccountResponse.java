package org.unipod.unipoderpbackend.dto.submission;

/**
 * The minimal submitter account created during an assisted submission.
 *
 * <p>{@code temporaryPassword} is returned only once, when the account is
 * created, so the receptionist can hand it to the submitter. It is never stored
 * in plaintext or returned again. {@code setPasswordToken} and
 * {@code setPasswordUrl} let the submitter choose their own password instead of
 * using the temporary password.</p>
 */
public record AssistedAccountResponse(
        String email,
        boolean created,
        String temporaryPassword,
        String setPasswordToken,
        String setPasswordUrl
) {
    public AssistedAccountResponse(String email, boolean created, String temporaryPassword) {
        this(email, created, temporaryPassword, null, null);
    }
}
