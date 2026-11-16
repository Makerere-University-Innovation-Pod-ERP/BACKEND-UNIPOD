package org.unipod.unipoderpbackend.exception.operations;

public class OperationsNotFoundException extends RuntimeException {
    private final String code;
    public OperationsNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }
    public OperationsNotFoundException(String message) {
        this("NOT_FOUND", message);
    }
    public String getCode() { return code; }
}
