package org.unipod.unipoderpbackend.repository.rules;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.rules.DepartmentRule;

public interface DepartmentRuleRepository extends JpaRepository<DepartmentRule, UUID> {
    List<DepartmentRule> findAllByOrderByUpdatedAtDescTitleAsc();

    List<DepartmentRule> findAllByPublishedTrueOrderByUpdatedAtDescTitleAsc();
}
