package org.unipod.unipoderpbackend.exception.rules;

import java.util.List;
import org.unipod.unipoderpbackend.dto.FieldErrorResponse;

public class InvalidRuleException extends RuntimeException {
    private final List<FieldErrorResponse> errors;

    public InvalidRuleException(FieldErrorResponse... errors) {
        this(List.of(errors));
    }

    public InvalidRuleException(List<FieldErrorResponse> errors) {
        super("Rule details are invalid.");
        this.errors = List.copyOf(errors);
    }

    public List<FieldErrorResponse> getErrors() {
        return errors;
    }
}
