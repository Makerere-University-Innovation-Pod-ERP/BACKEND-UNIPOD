package org.unipod.unipoderpbackend.repository.stock;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.operations.StockItem;

/**
 * The list queries of the stock screens. Locking and saving a single item still go through
 * {@code StockItemRepository}, which belongs to the orders code and is left as it is.
 */
public interface StockQueryRepository extends Repository<StockItem, UUID> {
    /** Matches ix_stock_items_department_name. */
    @Query("select s from StockItem s where s.department = :department order by lower(s.name), s.sku")
    List<StockItem> findByDepartmentOrdered(@Param("department") String department);

    /**
     * Newest first, with the item name in the same query (matches ix_stock_movements_created). Movements written
     * before V52 have no type or balance and are left out.
     */
    @Query("select new org.unipod.unipoderpbackend.repository.stock.StockMovementView(m, i.name) "
            + "from StockMovement m, StockItem i where i.id = m.stockItemId and i.department = :department "
            + "and m.movementType is not null order by m.createdAt desc, m.id desc")
    List<StockMovementView> findMovementViews(@Param("department") String department, Pageable page);

    @Query("select count(m) from StockMovement m, StockItem i where i.id = m.stockItemId "
            + "and i.department = :department and m.movementType is not null and m.createdAt >= :since")
    long countMovementsSince(@Param("department") String department, @Param("since") Instant since);

    @Query("select count(s) > 0 from StockItem s where upper(s.sku) = upper(:sku)")
    boolean existsBySkuIgnoringCase(@Param("sku") String sku);
}
