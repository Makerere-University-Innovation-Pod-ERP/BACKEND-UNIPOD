package org.unipod.unipoderpbackend.service.operations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.operations.DepartmentMembershipQuery;

class DepartmentAccessTest {
    private final DepartmentMembershipQuery memberships = mock(DepartmentMembershipQuery.class);
    private final DepartmentAccess access = new DepartmentAccess(memberships);

    @Test
    void administratorsActInEveryDepartment() {
        Actor admin = actor(ApiUserRole.ADMINISTRATOR);

        for (DepartmentCode department : DepartmentCode.values()) {
            assertDoesNotThrow(() -> access.require(admin, department));
        }
    }

    @Test
    void aCoordinatorActsOnlyInTheirOwnDepartment() {
        when(memberships.activeSectorNames(anyCollection())).thenReturn(List.of("Telecoms"));
        Actor coordinator = actor(ApiUserRole.DEPARTMENT_COORDINATOR);

        assertDoesNotThrow(() -> access.require(coordinator, DepartmentCode.TELECOMS));
        assertThrows(AccessDeniedException.class, () -> access.require(coordinator, DepartmentCode.TEXTILE));
    }

    @Test
    void aCoordinatorWithNoMatchingSectorActsNowhere() {
        when(memberships.activeSectorNames(anyCollection())).thenReturn(List.of("Agriculture and Food"));
        Actor coordinator = actor(ApiUserRole.DEPARTMENT_COORDINATOR);

        for (DepartmentCode department : DepartmentCode.values()) {
            assertThrows(AccessDeniedException.class, () -> access.require(coordinator, department));
        }
    }

    @Test
    void aCoordinatorOfSeveralDepartmentsActsInAllOfThem() {
        when(memberships.activeSectorNames(anyCollection())).thenReturn(List.of("textile", "CNC"));

        assertEquals(Set.of(DepartmentCode.TEXTILE, DepartmentCode.CNC),
                access.departmentsOf(actor(ApiUserRole.DEPARTMENT_COORDINATOR)));
    }

    @Test
    void otherRolesAreNotTiedToADepartmentHere() {
        Actor reviewer = actor(ApiUserRole.REVIEWER);

        assertDoesNotThrow(() -> access.require(reviewer, DepartmentCode.CNC));
    }

    @Test
    void sectorNamesMatchTheDepartmentAliases() {
        assertEquals(Optional.of(DepartmentCode.TELECOMS), DepartmentSectorNames.codeOf(" Telecom Lab "));
        assertEquals(Optional.of(DepartmentCode.MULTIMEDIA), DepartmentSectorNames.codeOf("MEDIA"));
        assertEquals(Optional.empty(), DepartmentSectorNames.codeOf("Digital Technology"));
        assertEquals(Optional.empty(), DepartmentSectorNames.codeOf(null));
    }

    private static Actor actor(ApiUserRole role) {
        return new Actor("person-" + UUID.randomUUID() + "@test.local", role, Set.of(UUID.randomUUID()));
    }
}
