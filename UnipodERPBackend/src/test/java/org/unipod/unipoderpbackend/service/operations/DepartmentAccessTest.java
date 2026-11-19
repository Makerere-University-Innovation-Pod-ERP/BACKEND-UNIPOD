package org.unipod.unipoderpbackend.service.operations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.exception.operations.DepartmentNotSetUpException;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.support.OperationsTestData;

@SpringBootTest
@Transactional
class DepartmentAccessTest {
    @Autowired
    private DepartmentAccess access;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID telecoms;
    private UUID textile;

    @BeforeEach
    void seed() {
        telecoms = OperationsTestData.sector(jdbcTemplate, "Telecoms");
        textile = OperationsTestData.sector(jdbcTemplate, "Textile");
    }

    @Test
    void anAdministratorMayActInEveryDepartment() {
        Actor admin = new Actor("admin@test.local", ApiUserRole.ADMINISTRATOR, Set.of());

        for (DepartmentCode department : DepartmentCode.values()) {
            assertDoesNotThrow(() -> access.require(admin, department));
            assertDoesNotThrow(() -> access.requireManage(admin, department));
        }
    }

    @Test
    void aCoordinatorMayActOnlyInDepartmentsTheyBelongTo() {
        Actor coordinator = new Actor("c@test.local", ApiUserRole.DEPARTMENT_COORDINATOR, Set.of(telecoms));

        assertDoesNotThrow(() -> access.require(coordinator, DepartmentCode.TELECOMS));
        assertDoesNotThrow(() -> access.requireManage(coordinator, DepartmentCode.TELECOMS));
        assertThrows(AccessDeniedException.class, () -> access.require(coordinator, DepartmentCode.TEXTILE));
        assertThrows(AccessDeniedException.class, () -> access.requireManage(coordinator, DepartmentCode.TEXTILE));
    }

    @Test
    void aCoordinatorOfDepartmentAliasesMayManageThatDepartment() {
        UUID media = OperationsTestData.sector(jdbcTemplate, "Media");
        OperationsTestData.sector(jdbcTemplate, "Multimedia");
        Actor coordinator = new Actor("c@test.local", ApiUserRole.DEPARTMENT_COORDINATOR, Set.of(media));

        assertDoesNotThrow(() -> access.requireManage(coordinator, DepartmentCode.MULTIMEDIA));
        assertEquals(Set.of(DepartmentCode.MULTIMEDIA), access.departmentsOf(coordinator));
    }

    @Test
    void departmentsOfIncludesEveryMatchingSectorTheCoordinatorBelongsTo() {
        UUID cnc = OperationsTestData.sector(jdbcTemplate, "CNC");
        Actor coordinator = new Actor(
                "c@test.local",
                ApiUserRole.DEPARTMENT_COORDINATOR,
                Set.of(telecoms, textile, cnc));

        assertEquals(Set.of(DepartmentCode.TELECOMS, DepartmentCode.TEXTILE, DepartmentCode.CNC),
                access.departmentsOf(coordinator));
    }

    @Test
    void aCoordinatorWithNoDepartmentIsRefused() {
        Actor coordinator = new Actor("c@test.local", ApiUserRole.DEPARTMENT_COORDINATOR, Set.of());

        assertThrows(AccessDeniedException.class, () -> access.require(coordinator, DepartmentCode.TELECOMS));
        assertThrows(AccessDeniedException.class, () -> access.requireManage(coordinator, DepartmentCode.TELECOMS));
    }

    @Test
    void aCoordinatorIsRefusedWhenTheDepartmentHasNoSectorYet() {
        Actor coordinator = new Actor("c@test.local", ApiUserRole.DEPARTMENT_COORDINATOR, Set.of(textile));

        assertThrows(AccessDeniedException.class, () -> access.requireManage(coordinator, DepartmentCode.CNC));
    }

    @Test
    void otherRolesAreNotTiedToADepartmentButCannotManageOne() {
        Actor observer = new Actor("o@test.local", ApiUserRole.OBSERVER, Set.of(telecoms));

        assertDoesNotThrow(() -> access.require(observer, DepartmentCode.TELECOMS));
        assertThrows(AccessDeniedException.class, () -> access.requireManage(observer, DepartmentCode.TELECOMS));
    }

    @Test
    void sectorNamesMatchTheDepartmentAliases() {
        assertEquals(Optional.of(DepartmentCode.TELECOMS), DepartmentSectorNames.codeOf(" Telecom Lab "));
        assertEquals(Optional.of(DepartmentCode.MULTIMEDIA), DepartmentSectorNames.codeOf("MEDIA"));
        assertEquals(Optional.of(DepartmentCode.TELECOMS), DepartmentSectorNames.codeOf("telecom-lab"));
        assertEquals(Optional.of(DepartmentCode.TELECOMS), DepartmentSectorNames.codeOf("TELECOM_LAB"));
        assertEquals(Optional.empty(), DepartmentSectorNames.codeOf("Digital Technology"));
        assertEquals(Optional.empty(), DepartmentSectorNames.codeOf(null));
    }

    @Test
    void findsTheSectorAndFacilityOfADepartment() {
        UUID facility = OperationsTestData.exclusiveFacility(jdbcTemplate, "Telecoms", 12);

        assertEquals(telecoms, access.requireSector(DepartmentCode.TELECOMS));
        assertEquals(facility, access.requireFacility(DepartmentCode.TELECOMS));
    }

    @Test
    void anInactiveSectorOrMissingFacilityIsReportedAsNotSetUp() {
        jdbcTemplate.update("UPDATE sectors SET active = FALSE WHERE id = ?", textile);
        jdbcTemplate.update("UPDATE facilities SET department = 'moved by test' WHERE lower(btrim(department)) = 'cnc'");

        assertThrows(DepartmentNotSetUpException.class, () -> access.requireSector(DepartmentCode.TEXTILE));
        assertThrows(DepartmentNotSetUpException.class, () -> access.requireFacility(DepartmentCode.CNC));
    }
}
