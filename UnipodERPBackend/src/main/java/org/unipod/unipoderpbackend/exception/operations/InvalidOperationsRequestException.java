package org.unipod.unipoderpbackend.exception.operations;

public class InvalidOperationsRequestException extends RuntimeException {
    private final String code;
    public InvalidOperationsRequestException(String code, String message) {
        super(message);
        this.code = code;
    }
    public InvalidOperationsRequestException(String message) { this("OPERATIONS_INVALID", message); }
    public String getCode() { return code; }
}
