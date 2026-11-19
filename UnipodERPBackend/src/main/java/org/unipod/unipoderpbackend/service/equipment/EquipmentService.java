package org.unipod.unipoderpbackend.service.equipment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.equipment.CreateEquipmentRequest;
import org.unipod.unipoderpbackend.exception.equipment.EquipmentConflictException;
import org.unipod.unipoderpbackend.exception.equipment.EquipmentNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;
import org.unipod.unipoderpbackend.model.equipment.FaultReport;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.equipment.EquipmentItemRepository;
import org.unipod.unipoderpbackend.repository.equipment.FaultReportRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.operations.ConstraintViolations;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;
import org.unipod.unipoderpbackend.service.operations.ManagerNotifier;

/** The equipment register of a department: list it, add to it, report a fault on an item. */
@Service
public class EquipmentService {
    static final String ENTITY = "equipment_item";

    private final EquipmentItemRepository equipment;
    private final FaultReportRepository reports;
    private final DepartmentAccess access;
    private final AuditService audit;
    private final ManagerNotifier notifier;
    private final Clock clock;

    public EquipmentService(EquipmentItemRepository equipment, FaultReportRepository reports, DepartmentAccess access,
                            AuditService audit, ManagerNotifier notifier, Clock clock) {
        this.equipment = equipment;
        this.reports = reports;
        this.access = access;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<EquipmentItem> list(DepartmentCode department) {
        return equipment.findByDepartmentOrdered(department);
    }

    @Transactional
    public EquipmentItem register(Actor actor, DepartmentCode department, CreateEquipmentRequest request) {
        access.require(actor, department);
        if (equipment.existsByAssetTagIgnoringCase(request.assetTag().trim())) {
            throw tagTaken();
        }
        EquipmentItem item = new EquipmentItem(UUID.randomUUID(), department, request.name(), request.category(),
                request.assetTag(), request.totalQuantity(), request.location(), request.note(), clock.instant());
        try {
            equipment.saveAndFlush(item);
        } catch (DataIntegrityViolationException raced) {
            // Two people registered the same tag at once and the unique index decided; any other failure is not a clash.
            if (ConstraintViolations.named(raced, "uq_equipment_items_asset_tag")) {
                throw tagTaken();
            }
            throw raced;
        }
        audit.log(AuditEvent.created(actor, "equipment.registered", ENTITY, item.getId(), EquipmentAuditSnapshot.of(item)));
        return item;
    }

    /** Flags the item faulty, keeps the report, tells the managers. The description never reaches the audit log. */
    @Transactional
    public EquipmentItem reportFault(Actor actor, DepartmentCode department, UUID equipmentId, String description) {
        access.require(actor, department);
        EquipmentItem item = lockInDepartment(equipmentId, department);
        EquipmentAuditSnapshot before = EquipmentAuditSnapshot.of(item);
        Instant now = clock.instant();

        item.reportFault(description, now);
        FaultReport report = FaultReport.forEquipment(item.getId(), description, actor.email(), now);
        reports.save(report);

        audit.log(AuditEvent.changed(actor, "equipment.fault_reported", ENTITY, item.getId(), before,
                EquipmentAuditSnapshot.of(item, report.getId())));
        notifier.notifyManagers(department, NotificationType.FAULT_REPORTED,
                "Fault reported: " + item.getName(),
                "A fault was reported on " + item.getName() + " (" + item.getAssetTag() + ").", ENTITY, item.getId());
        return item;
    }

    @Transactional
    public EquipmentItem markWorking(Actor actor, DepartmentCode department, UUID equipmentId, String note) {
        access.require(actor, department);
        EquipmentItem item = lockInDepartment(equipmentId, department);
        EquipmentAuditSnapshot before = EquipmentAuditSnapshot.of(item);

        item.markWorking(note, clock.instant());

        audit.log(AuditEvent.changed(actor, "equipment.marked_working", ENTITY, item.getId(), before,
                EquipmentAuditSnapshot.of(item)));
        return item;
    }

    /** Locks the row for the rest of the transaction; an item of another department is as good as missing. */
    EquipmentItem lockInDepartment(UUID equipmentId, DepartmentCode department) {
        return equipment.findByIdForUpdate(equipmentId)
                .filter(item -> item.getDepartment() == department)
                .orElseThrow(EquipmentNotFoundException::equipment);
    }

    private static EquipmentConflictException tagTaken() {
        return new EquipmentConflictException("That asset tag is already in the register.");
    }
}
