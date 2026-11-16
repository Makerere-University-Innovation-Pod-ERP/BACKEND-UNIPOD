package org.unipod.unipoderpbackend.exception.operations;

public class OperationsConflictException extends RuntimeException {
    private final String code;
    public OperationsConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
    public OperationsConflictException(String message) { this("OPERATIONS_CONFLICT", message); }
    public String getCode() { return code; }
}
