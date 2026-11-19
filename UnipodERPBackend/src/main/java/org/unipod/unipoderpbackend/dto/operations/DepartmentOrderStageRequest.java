package org.unipod.unipoderpbackend.dto.operations;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record DepartmentOrderStageRequest(
        @NotBlank String stage,
        JsonNode materials_used,
        String machine_time_used,
        JsonNode quality_checklist,
        String collection_signature,
        String notes
) {}

