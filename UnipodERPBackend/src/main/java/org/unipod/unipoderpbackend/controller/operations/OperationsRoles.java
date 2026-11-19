package org.unipod.unipoderpbackend.controller.operations;

/** Role expressions shared by the operations controllers. */
public final class OperationsRoles {
    // Every role except SUBMITTER may read operations data (D-011); OBSERVER is read-only.
    public static final String READ = "hasAnyRole('ADMINISTRATOR', 'DEPARTMENT_COORDINATOR', "
            + "'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'REVIEWER', "
            + "'SECTOR_OFFICER', 'IP_LIAISON', 'FINANCE_OFFICER', 'OBSERVER')";

    private OperationsRoles() {
    }
}
