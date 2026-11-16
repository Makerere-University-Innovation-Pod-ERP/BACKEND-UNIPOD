package org.unipod.unipoderpbackend.dto.operations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AllocationReturnRequest(@NotNull Instant returned_at, @NotBlank String condition_returned, String notes) {}

