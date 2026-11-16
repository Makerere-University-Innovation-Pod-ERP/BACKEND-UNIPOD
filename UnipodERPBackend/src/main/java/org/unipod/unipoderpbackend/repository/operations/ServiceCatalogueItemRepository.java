package org.unipod.unipoderpbackend.repository.operations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.ServiceCatalogueItem;

public interface ServiceCatalogueItemRepository extends JpaRepository<ServiceCatalogueItem, UUID> {
    List<ServiceCatalogueItem> findAllByActiveTrueOrderByDepartmentAscSortOrderAscNameAsc();
    List<ServiceCatalogueItem> findAllByDepartmentAndActiveTrueOrderBySortOrderAscNameAsc(DepartmentCode department);
}

