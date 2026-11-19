package org.unipod.unipoderpbackend.service.operations;

/**
 * Tells which database constraint a failure came from. A unique index is only a "duplicate" when it is the expected
 * one: any other integrity error (a rejected character, a value out of range) must not be reported as a name clash.
 */
public final class ConstraintViolations {
    private ConstraintViolations() {
    }

    public static boolean named(Throwable failure, String constraint) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
