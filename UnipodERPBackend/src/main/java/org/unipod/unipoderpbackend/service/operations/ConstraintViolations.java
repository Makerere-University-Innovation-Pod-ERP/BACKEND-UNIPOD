package org.unipod.unipoderpbackend.service.operations;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Tells which database constraint a failure came from. A unique index is only a "duplicate" when it is the expected
 * one: any other integrity error (a rejected character, a value out of range) must not be reported as a name clash.
 * The name is the one the database reported, so a value typed by a user can never pass for it.
 */
public final class ConstraintViolations {
    private ConstraintViolations() {
    }

    public static boolean named(Throwable failure, String constraint) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraint.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
