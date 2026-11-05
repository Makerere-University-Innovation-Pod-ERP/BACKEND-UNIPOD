package org.unipod.unipoderpbackend.submissions;

public class InvalidSubmissionException extends RuntimeException {
    public InvalidSubmissionException(String message) {
        super(message);
    }
}
