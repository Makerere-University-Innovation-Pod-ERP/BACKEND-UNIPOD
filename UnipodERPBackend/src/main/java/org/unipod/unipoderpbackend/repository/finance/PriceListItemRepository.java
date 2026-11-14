package org.unipod.unipoderpbackend.repository.finance;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.finance.PriceItemStatus;
import org.unipod.unipoderpbackend.model.finance.PriceListItem;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, UUID> {
    @Query("""
            select item from PriceListItem item
            where (:departmentId is null or item.departmentId = :departmentId)
              and (:status is null or item.status = :status)
            order by item.service, item.id
            """)
    List<PriceListItem> search(@Param("departmentId") UUID departmentId, @Param("status") PriceItemStatus status);
}
