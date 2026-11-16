package org.unipod.unipoderpbackend.service.operations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.unipod.unipoderpbackend.dto.operations.CreateServiceCatalogueItemRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.operations.ServiceCatalogueItemResponse;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.ServiceCatalogueItem;
import org.unipod.unipoderpbackend.repository.operations.ServiceCatalogueItemRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;

@Service
public class ServiceCatalogueService {
    private final ServiceCatalogueItemRepository items;
    private final AuditTrail auditTrail;
    private final Clock clock;
    public ServiceCatalogueService(ServiceCatalogueItemRepository items, AuditTrail auditTrail, Clock clock) {
        this.items = items;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public ServiceCatalogueItemResponse create(CreateServiceCatalogueItemRequest request, String actor) {
        Instant now = clock.instant();
        ServiceCatalogueItem item = new ServiceCatalogueItem(
                UUID.randomUUID(),
                request.department(),
                request.name().trim(),
                request.description(),
                request.price(),
                request.currency(),
                request.turnaroundEstimate(),
                request.whatsappGroupUrl(),
                request.requiresBrief(),
                request.active() == null || request.active(),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                now
        );
        ServiceCatalogueItem saved = items.saveAndFlush(item);
        auditTrail.record(actor, "service_catalogue.created", "service_catalogue", saved.getId(), null,
                AuditTrail.details("department", saved.getDepartment().name(), "price", saved.getPrice()));
        return ServiceCatalogueItemResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, List<ServiceCatalogueItemResponse>> catalogue() {
        Map<String, List<ServiceCatalogueItemResponse>> grouped = new LinkedHashMap<>();
        for (DepartmentCode dept : DepartmentCode.values()) grouped.put(dept.name(), List.of());
        items.findAllByActiveTrueOrderByDepartmentAscSortOrderAscNameAsc().stream()
                .map(ServiceCatalogueItemResponse::from)
                .collect(Collectors.groupingBy(ServiceCatalogueItemResponse::department, LinkedHashMap::new, Collectors.toList()))
                .forEach(grouped::put);
        return grouped;
    }
}

