package org.unipod.unipoderpbackend.model.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.exception.equipment.EquipmentConflictException;
import org.unipod.unipoderpbackend.exception.equipment.InvalidEquipmentException;

class EquipmentLoanTest {
    private static final Instant NOW = Instant.parse("2026-11-19T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-19T10:00:00Z");
    private static final LocalDate OUT = LocalDate.of(2026, 11, 19);
    private static final LocalDate BACK = LocalDate.of(2026, 11, 26);

    @Test
    void aNewLoanWaitsForApprovalWithNoDecision() {
        EquipmentLoan loan = loan();

        assertEquals(LoanStatus.PENDING_APPROVAL, loan.getStatus());
        assertNull(loan.getDecidedAt());
        assertEquals("Student", loan.getBorrowerType());
    }

    @Test
    void aRequestNeedsPositiveQuantityNamedBorrowerAndSaneDates() {
        assertThrows(InvalidEquipmentException.class, () -> request(0, "Peter", "0772", OUT, BACK));
        assertThrows(InvalidEquipmentException.class, () -> request(1, " ", "0772", OUT, BACK));
        assertThrows(InvalidEquipmentException.class, () -> request(1, "Peter", " ", OUT, BACK));
        assertThrows(InvalidEquipmentException.class, () -> request(1, "Peter", "0772", BACK, OUT));
        request(1, "Peter", "0772", OUT, OUT);
    }

    @Test
    void datesOutsideTheStorableRangeAreRefused() {
        assertThrows(InvalidEquipmentException.class,
                () -> request(1, "Peter", "0772", LocalDate.of(1850, 1, 1), BACK));
        assertThrows(InvalidEquipmentException.class,
                () -> request(1, "Peter", "0772", OUT, LocalDate.of(3000, 1, 1)));
    }

    @Test
    void approvingPutsTheLoanOutAndRecordsWhoDecidedAndWhen() {
        EquipmentLoan loan = loan();

        loan.approve("manager@test.local", "  For the field trial  ", LATER);

        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
        assertEquals("manager@test.local", loan.getDecidedBy());
        assertEquals(LATER, loan.getDecidedAt());
        assertEquals("For the field trial", loan.getDecisionNote());
    }

    @Test
    void decliningRecordsTheDecisionAndAnEmptyReasonBecomesNull() {
        EquipmentLoan loan = loan();

        loan.decline("manager@test.local", " ", LATER);

        assertEquals(LoanStatus.DECLINED, loan.getStatus());
        assertEquals(LATER, loan.getDecidedAt());
        assertNull(loan.getDecisionNote());
    }

    @Test
    void aDecidedLoanCannotBeDecidedAgain() {
        EquipmentLoan approved = loan();
        approved.approve("m@test.local", null, LATER);
        EquipmentLoan declined = loan();
        declined.decline("m@test.local", null, LATER);

        assertThrows(EquipmentConflictException.class, () -> approved.approve("m@test.local", null, LATER));
        assertThrows(EquipmentConflictException.class, () -> approved.decline("m@test.local", null, LATER));
        assertThrows(EquipmentConflictException.class, () -> declined.approve("m@test.local", null, LATER));
    }

    @Test
    void onlyAnActiveLoanCanBeReturnedAndNeedsACondition() {
        EquipmentLoan pending = loan();
        assertThrows(EquipmentConflictException.class, () -> pending.markReturned("m@test.local", "Fine", LATER));

        EquipmentLoan active = loan();
        active.approve("m@test.local", null, LATER);
        assertThrows(InvalidEquipmentException.class, () -> active.markReturned("m@test.local", "  ", LATER));
        active.markReturned("m@test.local", "  Clean and complete ", LATER);

        assertEquals(LoanStatus.RETURNED, active.getStatus());
        assertEquals("Clean and complete", active.getIncomingCondition());
        assertThrows(EquipmentConflictException.class, () -> active.markReturned("m@test.local", "Again", LATER));
    }

    @Test
    void anActiveLoanBecomesOverdueTheDayAfterItsReturnDate() {
        EquipmentLoan loan = loan();
        loan.approve("m@test.local", null, LATER);

        assertEquals(LoanStatus.ACTIVE, loan.statusOn(BACK));
        assertEquals(LoanStatus.OVERDUE, loan.statusOn(BACK.plusDays(1)));
    }

    @Test
    void onlyActiveLoansCanBeOverdue() {
        EquipmentLoan pending = loan();
        EquipmentLoan declined = loan();
        declined.decline("m@test.local", null, LATER);
        EquipmentLoan returned = loan();
        returned.approve("m@test.local", null, LATER);
        returned.markReturned("m@test.local", "Fine", LATER);
        LocalDate muchLater = BACK.plusDays(30);

        assertEquals(LoanStatus.PENDING_APPROVAL, pending.statusOn(muchLater));
        assertEquals(LoanStatus.DECLINED, declined.statusOn(muchLater));
        assertEquals(LoanStatus.RETURNED, returned.statusOn(muchLater));
    }

    private static EquipmentLoan loan() {
        return request(2, "Peter Okello", "0772000000", OUT, BACK);
    }

    private static EquipmentLoan request(int quantity, String borrower, String contact, LocalDate out, LocalDate back) {
        return EquipmentLoan.request(UUID.randomUUID(), "LOAN-" + UUID.randomUUID().toString().substring(0, 6),
                UUID.randomUUID(), quantity, borrower, null, contact, out, back, " Clean ", "staff@test.local", NOW);
    }
}
