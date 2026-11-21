package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.unipod.unipoderpbackend.dto.operations.NoNullCharacters;

public record TextileReasonRequest(@NotBlank @Size(max = 1000) @NoNullCharacters String reason) {}
