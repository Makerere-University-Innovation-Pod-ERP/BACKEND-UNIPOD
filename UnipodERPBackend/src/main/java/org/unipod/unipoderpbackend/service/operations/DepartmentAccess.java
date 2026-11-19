package org.unipod.unipoderpbackend.service.operations;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.unipod.unipoderpbackend.exception.operations.DepartmentNotSetUpException;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.operations.DepartmentLookup;
import org.unipod.unipoderpbackend.repository.operations.DepartmentMembershipQuery;

/**
 * Resolves the departments an actor may access and the sector or facility backing each department.
 */
@Service
public class DepartmentAccess {
    private final DepartmentLookup departments;
    private final DepartmentMembershipQuery memberships;

    public DepartmentAccess(DepartmentLookup departments, DepartmentMembershipQuery memberships) {
        this.departments = departments;
        this.memberships = memberships;
    }

    /**
     * Enforces the department boundary for actions whose endpoint role gate also allows non-manager roles.
     */
    public void require(Actor actor, DepartmentCode department) {
        if (actor.role() != ApiUserRole.DEPARTMENT_COORDINATOR) {
            return;
        }
        if (!departmentsOf(actor).contains(department)) {
            throw new AccessDeniedException("Not a member of the " + department + " department.");
        }
    }

    /**
     * Enforces manager-only actions: administrators may act anywhere; coordinators only in their departments.
     */
    public void requireManage(Actor actor, DepartmentCode department) {
        if (actor.role() == ApiUserRole.ADMINISTRATOR) {
            return;
        }
        if (actor.role() != ApiUserRole.DEPARTMENT_COORDINATOR || !departmentsOf(actor).contains(department)) {
            throw new AccessDeniedException("You can only manage your own department.");
        }
    }

    public Set<DepartmentCode> departmentsOf(Actor actor) {
        Set<DepartmentCode> departments = EnumSet.noneOf(DepartmentCode.class);
        for (String sectorName : memberships.activeSectorNames(actor.departmentIds())) {
            DepartmentSectorNames.codeOf(sectorName).ifPresent(departments::add);
        }
        return departments;
    }

    /** The department's sector, for data that a sector owns (training sessions). */
    public UUID requireSector(DepartmentCode department) {
        return departments.sectorId(department).orElseThrow(() -> new DepartmentNotSetUpException(
                "The " + label(department) + " department has not been set up yet."));
    }

    /** The department's bookable facility. */
    public UUID requireFacility(DepartmentCode department) {
        return departments.facilityId(department).orElseThrow(() -> new DepartmentNotSetUpException(
                "No facility has been set up for the " + label(department) + " department yet."));
    }

    private static String label(DepartmentCode department) {
        String name = department.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
