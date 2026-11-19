package org.unipod.unipoderpbackend.service.operations;

import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.operations.DepartmentMembershipQuery;

/**
 * Which department a person may act in. The role gates on the controllers decide whether someone may do a kind of
 * thing; this decides where. Administrators act everywhere. A department coordinator acts only in the departments
 * they belong to. Other roles that may report faults or request loans are not tied to a department.
 */
@Service
public class DepartmentAccess {
    private final DepartmentMembershipQuery memberships;

    public DepartmentAccess(DepartmentMembershipQuery memberships) {
        this.memberships = memberships;
    }

    public void require(Actor actor, DepartmentCode department) {
        if (actor.role() != ApiUserRole.DEPARTMENT_COORDINATOR) {
            return;
        }
        if (!departmentsOf(actor).contains(department)) {
            throw new AccessDeniedException("Not a member of the " + department + " department.");
        }
    }

    public Set<DepartmentCode> departmentsOf(Actor actor) {
        Set<DepartmentCode> departments = EnumSet.noneOf(DepartmentCode.class);
        for (String sectorName : memberships.activeSectorNames(actor.departmentIds())) {
            DepartmentSectorNames.codeOf(sectorName).ifPresent(departments::add);
        }
        return departments;
    }
}
