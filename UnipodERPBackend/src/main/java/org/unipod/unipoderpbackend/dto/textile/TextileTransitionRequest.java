package org.unipod.unipoderpbackend.dto.textile;

import jakarta.validation.constraints.NotBlank;

public record TextileTransitionRequest(@NotBlank String toStatus) {}
