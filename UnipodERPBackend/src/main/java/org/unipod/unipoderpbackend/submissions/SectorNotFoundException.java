package org.unipod.unipoderpbackend.submissions;

import java.util.UUID;

public class SectorNotFoundException extends RuntimeException {
    public SectorNotFoundException(UUID id) {
        super("Sector not found: " + id);
    }
}
