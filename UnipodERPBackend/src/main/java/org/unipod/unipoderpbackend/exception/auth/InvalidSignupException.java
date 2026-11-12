package org.unipod.unipoderpbackend.exception.auth;

import java.util.List;
import org.unipod.unipoderpbackend.dto.FieldErrorResponse;

public class InvalidSignupException extends RuntimeException {
    private final List<FieldErrorResponse> errors;

    public InvalidSignupException(List<FieldErrorResponse> errors) {
        super("Sign-up details are incomplete.");
        this.errors = List.copyOf(errors);
    }

    public List<FieldErrorResponse> getErrors() {
        return errors;
    }
}