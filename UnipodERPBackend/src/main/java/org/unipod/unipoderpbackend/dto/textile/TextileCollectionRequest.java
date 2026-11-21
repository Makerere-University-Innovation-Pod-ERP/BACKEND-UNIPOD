package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;

public record TextileCollectionRequest(@NotBlank @Size(max = 200) @NoNullCharacters String collectedBy) {}
