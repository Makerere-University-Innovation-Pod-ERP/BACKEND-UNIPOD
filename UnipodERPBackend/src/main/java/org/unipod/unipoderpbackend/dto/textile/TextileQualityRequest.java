package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TextileQualityRequest(@NotEmpty List<@NotNull @Valid QualityItem> checklist) {
    public record QualityItem(@NotBlank @Size(max = 80) String id, boolean checked, String label) {}
}
