package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;

public record TextileQualityRequest(@NotNull List<@NotNull @Valid QualityItem> checklist) {
    public record QualityItem(@NotNull @Size(max = 80) String id, boolean checked, String label) {}
}
