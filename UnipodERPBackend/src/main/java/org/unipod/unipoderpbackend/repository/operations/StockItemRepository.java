package org.unipod.unipoderpbackend.repository.operations;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.operations.StockItem;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where upper(s.sku) = upper(:sku)")
    Optional<StockItem> findBySkuForUpdate(@Param("sku") String sku);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.id = :id")
    Optional<StockItem> findByIdForUpdate(@Param("id") UUID id);
}

