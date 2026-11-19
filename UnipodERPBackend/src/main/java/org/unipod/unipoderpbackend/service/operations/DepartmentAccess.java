package org.unipod.unipoderpbackend.service.operations;

import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.unipod.unipoderpbackend.exception.operations.DepartmentNotSetUpException;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.operations.DepartmentLookup;

/**
 * Who may change a department's data (D-047): administrators anywhere, a department coordinator only
 * in a department they are a member of. The role gate on the endpoint has already let both roles in.
 */
@Service
public class DepartmentAccess {
    private final DepartmentLookup departments;

    public DepartmentAccess(DepartmentLookup departments) {
        this.departments = departments;
    }

    public void requireManage(Actor actor, DepartmentCode department) {
        if (actor.role() == ApiUserRole.ADMINISTRATOR) {
            return;
        }
        boolean member = actor.role() == ApiUserRole.DEPARTMENT_COORDINATOR
                && departments.sectorIds(department).stream().anyMatch(actor.departmentIds()::contains);
        if (!member) {
            throw new AccessDeniedException("You can only manage your own department.");
        }
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
