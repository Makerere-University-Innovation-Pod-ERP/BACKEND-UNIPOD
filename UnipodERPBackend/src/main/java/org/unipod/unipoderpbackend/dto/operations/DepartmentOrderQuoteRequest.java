package org.unipod.unipoderpbackend.dto.operations;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record DepartmentOrderQuoteRequest(@NotNull JsonNode quote) {}

