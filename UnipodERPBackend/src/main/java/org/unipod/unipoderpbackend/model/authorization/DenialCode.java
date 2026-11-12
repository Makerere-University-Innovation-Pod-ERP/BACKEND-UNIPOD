package org.unipod.unipoderpbackend.model.authorization;

public enum DenialCode {
    ROLE_NOT_PERMITTED("Your role is not allowed to do this."),
    NOT_OWNER("You can only do this on your own records."),
    NOT_ASSIGNED("You can only do this on records assigned to you."),
    OUTSIDE_DEPARTMENT("You can only do this on records in your department.");

    private final String message;

    DenialCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
