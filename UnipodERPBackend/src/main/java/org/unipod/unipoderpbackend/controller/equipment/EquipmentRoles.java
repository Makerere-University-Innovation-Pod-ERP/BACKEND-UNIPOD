package org.unipod.unipoderpbackend.controller.equipment;

/**
 * Role expressions for the equipment, machine and stock endpoints (D-011, D-046). Every role except SUBMITTER may
 * read; OBSERVER only reads; MANAGE decides. Which department a coordinator may act in is checked in the service.
 */
public final class EquipmentRoles {
    public static final String READ = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', 'INNOVATION_COORDINATOR', "
            + "'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', 'SECTOR_OFFICER', 'IP_LIAISON', "
            + "'FINANCE_OFFICER', 'OBSERVER')";
    public static final String REPORT = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', 'INNOVATION_COORDINATOR', "
            + "'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', 'SECTOR_OFFICER', 'IP_LIAISON', 'FINANCE_OFFICER')";
    public static final String MANAGE = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR')";

    private EquipmentRoles() {
    }
}
