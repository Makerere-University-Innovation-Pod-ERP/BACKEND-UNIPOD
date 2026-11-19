package org.unipod.unipoderpbackend.service.equipment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.equipment.CreateMachineRequest;
import org.unipod.unipoderpbackend.dto.equipment.ReportMachineRequest;
import org.unipod.unipoderpbackend.exception.equipment.EquipmentConflictException;
import org.unipod.unipoderpbackend.exception.equipment.EquipmentNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.equipment.FaultKind;
import org.unipod.unipoderpbackend.model.equipment.FaultReport;
import org.unipod.unipoderpbackend.model.equipment.Machine;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.equipment.FaultReportRepository;
import org.unipod.unipoderpbackend.repository.equipment.MachineRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.operations.ConstraintViolations;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;
import org.unipod.unipoderpbackend.service.operations.ManagerNotifier;

/** The workshop machines of a department: list them, add one, report a fault or a service, mark one working again. */
@Service
public class MachineService {
    static final String ENTITY = "machine";

    private final MachineRepository machines;
    private final FaultReportRepository reports;
    private final DepartmentAccess access;
    private final AuditService audit;
    private final ManagerNotifier notifier;
    private final Clock clock;

    public MachineService(MachineRepository machines, FaultReportRepository reports, DepartmentAccess access,
                          AuditService audit, ManagerNotifier notifier, Clock clock) {
        this.machines = machines;
        this.reports = reports;
        this.access = access;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** A report, the machine it changed and the managers who were told. */
    public record ReportResult(Machine machine, FaultReport report, List<String> managersNotified) {
    }

    @Transactional(readOnly = true)
    public List<Machine> list(DepartmentCode department) {
        return machines.findByDepartmentOrdered(department);
    }

    @Transactional
    public Machine register(Actor actor, DepartmentCode department, CreateMachineRequest request) {
        access.require(actor, department);
        if (machines.existsByDepartmentAndNameIgnoringCase(department, request.name().trim())) {
            throw nameTaken();
        }
        Machine machine = new Machine(UUID.randomUUID(), department, request.name(), request.state(), request.note(),
                clock.instant());
        try {
            machines.saveAndFlush(machine);
        } catch (DataIntegrityViolationException raced) {
            // Two people added the same machine at once and the unique index decided; any other failure is not a clash.
            if (ConstraintViolations.named(raced, "uq_machines_department_name")) {
                throw nameTaken();
            }
            throw raced;
        }
        audit.log(AuditEvent.created(actor, "machine.registered", ENTITY, machine.getId(), MachineAuditSnapshot.of(machine)));
        return machine;
    }

    /**
     * Records a fault or a service request, flags the machine and tells the managers. The description and the
     * photo name stay in the report row; the audit log only gets the state change and the report id.
     */
    @Transactional
    public ReportResult report(Actor actor, DepartmentCode department, UUID machineId, ReportMachineRequest request,
                               PhotoMeta photo) {
        access.require(actor, department);
        String photoName = photo == null ? null : photo.checkedName();
        Machine machine = lockInDepartment(machineId, department);
        MachineAuditSnapshot before = MachineAuditSnapshot.of(machine);
        FaultKind kind = request.kind() == null ? FaultKind.FAULT : request.kind();
        Instant now = clock.instant();

        machine.report(kind, request.description(), now);
        FaultReport report = FaultReport.forMachine(machine.getId(), kind, request.description(), photoName,
                actor.email(), now);
        reports.save(report);

        audit.log(AuditEvent.changed(actor,
                kind == FaultKind.FAULT ? "machine.fault_reported" : "machine.service_requested", ENTITY,
                machine.getId(), before, MachineAuditSnapshot.of(machine, kind, report.getId())));
        String title = (kind == FaultKind.FAULT ? "Fault reported: " : "Service due: ") + machine.getName();
        List<String> told = notifier.notifyManagers(department, NotificationType.FAULT_REPORTED, title,
                "A " + (kind == FaultKind.FAULT ? "fault" : "service request") + " was reported on "
                        + machine.getName() + ".", ENTITY, machine.getId());
        return new ReportResult(machine, report, told);
    }

    @Transactional
    public Machine markWorking(Actor actor, DepartmentCode department, UUID machineId, String note) {
        access.require(actor, department);
        Machine machine = lockInDepartment(machineId, department);
        MachineAuditSnapshot before = MachineAuditSnapshot.of(machine);

        machine.markWorking(note, clock.instant());

        audit.log(AuditEvent.changed(actor, "machine.marked_working", ENTITY, machine.getId(), before,
                MachineAuditSnapshot.of(machine)));
        return machine;
    }

    private Machine lockInDepartment(UUID machineId, DepartmentCode department) {
        return machines.findByIdForUpdate(machineId)
                .filter(machine -> machine.getDepartment() == department)
                .orElseThrow(EquipmentNotFoundException::machine);
    }

    private static EquipmentConflictException nameTaken() {
        return new EquipmentConflictException("A machine with that name is already on the list.");
    }
}
