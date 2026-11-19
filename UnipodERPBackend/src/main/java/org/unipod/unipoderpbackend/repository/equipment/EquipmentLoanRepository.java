package org.unipod.unipoderpbackend.repository.equipment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.equipment.EquipmentLoan;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;

public interface EquipmentLoanRepository extends JpaRepository<EquipmentLoan, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from EquipmentLoan l where l.code = :code")
    Optional<EquipmentLoan> findByCodeForUpdate(@Param("code") String code);

    /** Newest first, with the equipment name in the same query (matches ix_equipment_loans_created). */
    @Query("select new org.unipod.unipoderpbackend.repository.equipment.LoanView(l, e.name) "
            + "from EquipmentLoan l, EquipmentItem e where e.id = l.equipmentId and e.department = :department "
            + "order by l.createdAt desc, l.id desc")
    List<LoanView> findViewsByDepartment(@Param("department") DepartmentCode department, Pageable page);

    @Query(value = "select nextval('equipment_loan_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
