package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

public record TextileQualityRequest(@Valid List<QualityItem> checklist) {
    public record QualityItem(UUID id, boolean checked, String label) {}
}
