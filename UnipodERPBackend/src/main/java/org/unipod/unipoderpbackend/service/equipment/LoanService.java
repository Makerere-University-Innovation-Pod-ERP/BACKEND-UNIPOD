package org.unipod.unipoderpbackend.service.equipment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.equipment.LoanResponse;
import org.unipod.unipoderpbackend.dto.equipment.RequestLoanRequest;
import org.unipod.unipoderpbackend.exception.equipment.EquipmentNotFoundException;
import org.unipod.unipoderpbackend.model.audit.AuditEvent;
import org.unipod.unipoderpbackend.model.authorization.Actor;
import org.unipod.unipoderpbackend.model.equipment.EquipmentItem;
import org.unipod.unipoderpbackend.model.equipment.EquipmentLoan;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.model.operations.DepartmentCode;
import org.unipod.unipoderpbackend.repository.equipment.EquipmentItemRepository;
import org.unipod.unipoderpbackend.repository.equipment.EquipmentLoanRepository;
import org.unipod.unipoderpbackend.service.audit.AuditService;
import org.unipod.unipoderpbackend.service.operations.DepartmentAccess;
import org.unipod.unipoderpbackend.service.operations.ManagerNotifier;

/**
 * Equipment loans of a department. Approving reserves units on the equipment item and returning gives them back, in
 * one transaction. Every change locks the loan row first and then the equipment row, always in that order, so two
 * managers racing for the last unit are served one after the other and never deadlock.
 */
@Service
public class LoanService {
    static final String ENTITY = "equipment_loan";
    private static final String CODE_FORMAT = "LOAN-%06d";
    private static final int LIST_LIMIT = 500;

    private final EquipmentLoanRepository loans;
    private final EquipmentItemRepository equipment;
    private final DepartmentAccess access;
    private final AuditService audit;
    private final ManagerNotifier notifier;
    private final Clock clock;

    public LoanService(EquipmentLoanRepository loans, EquipmentItemRepository equipment, DepartmentAccess access,
                       AuditService audit, ManagerNotifier notifier, Clock clock) {
        this.loans = loans;
        this.equipment = equipment;
        this.access = access;
        this.audit = audit;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** The latest 500 loans of the department, newest first. */
    @Transactional(readOnly = true)
    public List<LoanResponse> list(DepartmentCode department) {
        LocalDate today = LocalDate.now(clock);
        return loans.findViewsByDepartment(department, PageRequest.of(0, LIST_LIMIT)).stream()
                .map(view -> LoanResponse.from(view.loan(), view.equipmentName(), today))
                .toList();
    }

    /** Asks for equipment. Nothing is reserved yet; the request only has to look possible when it is made. */
    @Transactional
    public LoanResponse request(Actor actor, DepartmentCode department, RequestLoanRequest request) {
        access.require(actor, department);
        EquipmentItem item = equipment.findById(request.equipmentId())
                .filter(found -> found.getDepartment() == department)
                .orElseThrow(EquipmentNotFoundException::equipment);
        int quantity = request.quantity() == null ? 1 : request.quantity();
        item.requireLendable(quantity);

        EquipmentLoan loan = EquipmentLoan.request(UUID.randomUUID(), String.format(CODE_FORMAT, loans.nextCodeNumber()),
                item.getId(), quantity, request.borrowerName(), request.borrowerType(), request.borrowerContact(),
                request.dateOut(), request.dateBack(), request.outgoingCondition(), actor.email(), clock.instant());
        loans.save(loan);

        audit.log(AuditEvent.created(actor, "equipment_loan.requested", ENTITY, loan.getId(),
                LoanAuditSnapshot.of(loan, item)));
        notifier.notifyManagers(department, NotificationType.EQUIPMENT_APPROVAL_REQUESTED,
                "Loan waiting for approval: " + item.getName(),
                "A loan of " + quantity + " x " + item.getName() + " is waiting for approval.", ENTITY, loan.getId());
        return respond(loan, item);
    }

    @Transactional
    public LoanResponse approve(Actor actor, DepartmentCode department, String code, String note) {
        access.require(actor, department);
        EquipmentLoan loan = lockLoan(code);
        EquipmentItem item = lockItem(loan, department);
        LoanAuditSnapshot before = LoanAuditSnapshot.of(loan, item);
        Instant now = clock.instant();

        loan.approve(actor.email(), note, now);
        item.reserve(loan.getQuantity(), now);

        audit.log(AuditEvent.changed(actor, "equipment_loan.approved", ENTITY, loan.getId(), before,
                LoanAuditSnapshot.of(loan, item)));
        return respond(loan, item);
    }

    @Transactional
    public LoanResponse decline(Actor actor, DepartmentCode department, String code, String reason) {
        access.require(actor, department);
        EquipmentLoan loan = lockLoan(code);
        EquipmentItem item = lockItem(loan, department);
        LoanAuditSnapshot before = LoanAuditSnapshot.of(loan, item);

        loan.decline(actor.email(), reason, clock.instant());

        audit.log(AuditEvent.changed(actor, "equipment_loan.declined", ENTITY, loan.getId(), before,
                LoanAuditSnapshot.of(loan, item)));
        return respond(loan, item);
    }

    @Transactional
    public LoanResponse markReturned(Actor actor, DepartmentCode department, String code, String condition) {
        access.require(actor, department);
        EquipmentLoan loan = lockLoan(code);
        EquipmentItem item = lockItem(loan, department);
        LoanAuditSnapshot before = LoanAuditSnapshot.of(loan, item);
        Instant now = clock.instant();

        loan.markReturned(actor.email(), condition, now);
        item.release(loan.getQuantity(), now);

        audit.log(AuditEvent.changed(actor, "equipment_loan.returned", ENTITY, loan.getId(), before,
                LoanAuditSnapshot.of(loan, item)));
        return respond(loan, item);
    }

    private EquipmentLoan lockLoan(String code) {
        return loans.findByCodeForUpdate(code).orElseThrow(EquipmentNotFoundException::loan);
    }

    /** A loan of another department's equipment is as good as missing here. */
    private EquipmentItem lockItem(EquipmentLoan loan, DepartmentCode department) {
        return equipment.findByIdForUpdate(loan.getEquipmentId())
                .filter(item -> item.getDepartment() == department)
                .orElseThrow(EquipmentNotFoundException::loan);
    }

    private LoanResponse respond(EquipmentLoan loan, EquipmentItem item) {
        return LoanResponse.from(loan, item.getName(), LocalDate.now(clock));
    }
}
