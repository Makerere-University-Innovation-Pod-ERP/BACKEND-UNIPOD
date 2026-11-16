package org.unipod.unipoderpbackend.dto.operations;

import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.ServiceCatalogueItem;

public record ServiceCatalogueItemResponse(
        UUID id, String department, String name, String description, long price, String currency,
        String turnaroundEstimate, String whatsappGroupUrl, boolean requiresBrief, boolean active, int sortOrder
) {
    public static ServiceCatalogueItemResponse from(ServiceCatalogueItem i) {
        return new ServiceCatalogueItemResponse(i.getId(), i.getDepartment().name(), i.getName(), i.getDescription(),
                i.getPrice(), i.getCurrency(), i.getTurnaroundEstimate(), i.getWhatsappGroupUrl(),
                i.isRequiresBrief(), i.isActive(), i.getSortOrder());
    }
}

