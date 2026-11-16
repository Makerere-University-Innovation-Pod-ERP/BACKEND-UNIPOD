package org.unipod.unipoderpbackend.service.operations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.operations.ServiceCatalogueItemResponse;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.operations.ServiceCatalogueItemRepository;

@Service
public class ServiceCatalogueService {
    private final ServiceCatalogueItemRepository items;
    public ServiceCatalogueService(ServiceCatalogueItemRepository items) { this.items = items; }

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

