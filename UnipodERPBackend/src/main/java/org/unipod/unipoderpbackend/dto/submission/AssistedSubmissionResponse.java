package org.unipod.unipoderpbackend.dto.submission;

public record AssistedSubmissionResponse(
        SubmissionResponse submission,
        AssistedAccountResponse account
) {
}
