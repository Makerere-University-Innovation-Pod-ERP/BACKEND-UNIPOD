package org.unipod.unipoderpbackend.repository.operations;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.model.operations.DepartmentOrder;

public interface DepartmentOrderRepository extends JpaRepository<DepartmentOrder, UUID> {
    Page<DepartmentOrder> findAllByDepartmentOrderByUpdatedAtDescCreatedAtDesc(
            DepartmentCode department, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from DepartmentOrder o where o.id = :id and o.department = :department")
    Optional<DepartmentOrder> findByIdAndDepartmentForUpdate(@Param("id") UUID id, @Param("department") DepartmentCode department);
    Optional<DepartmentOrder> findByIdAndDepartment(UUID id, DepartmentCode department);
    @Query(value = "select nextval('department_order_code_seq')", nativeQuery = true)
    long nextCodeSeq();
}

