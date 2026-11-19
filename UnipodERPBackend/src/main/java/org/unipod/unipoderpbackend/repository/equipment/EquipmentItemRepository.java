package org.unipod.unipoderpbackend.repository.equipment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

public interface EquipmentItemRepository extends JpaRepository<EquipmentItem, UUID> {
    /** Matches ix_equipment_items_department_name. */
    @Query("select e from EquipmentItem e where e.department = :department order by lower(e.name), e.assetTag")
    List<EquipmentItem> findByDepartmentOrdered(@Param("department") DepartmentCode department);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EquipmentItem e where e.id = :id")
    Optional<EquipmentItem> findByIdForUpdate(@Param("id") UUID id);

    @Query("select count(e) > 0 from EquipmentItem e where lower(e.assetTag) = lower(:assetTag)")
    boolean existsByAssetTagIgnoringCase(@Param("assetTag") String assetTag);
}
