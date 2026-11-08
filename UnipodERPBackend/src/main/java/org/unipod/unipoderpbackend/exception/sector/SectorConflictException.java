package org.unipod.unipoderpbackend.exception.sector;

public class SectorConflictException extends RuntimeException {
    public SectorConflictException() {
        super("A sector with this name already exists.");
    }
}
