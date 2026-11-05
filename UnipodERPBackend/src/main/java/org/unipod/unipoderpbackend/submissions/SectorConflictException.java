package org.unipod.unipoderpbackend.submissions;

public class SectorConflictException extends RuntimeException {
    public SectorConflictException() {
        super("A sector with this name already exists.");
    }
}
