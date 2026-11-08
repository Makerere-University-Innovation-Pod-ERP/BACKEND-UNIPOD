package org.unipod.unipoderpbackend.exception.submission;

import java.util.UUID;

public class SubmissionNotFoundException extends RuntimeException {
    public SubmissionNotFoundException(UUID id) {
        super("Submission %s was not found.".formatted(id));
    }
}
