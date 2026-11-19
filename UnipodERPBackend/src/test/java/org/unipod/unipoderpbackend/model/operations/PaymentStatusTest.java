package org.unipod.unipoderpbackend.model.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentStatusTest {
    @ParameterizedTest
    @CsvSource({
            "WAITING_FOR_PROOF, , AWAITING_PROOF",
            "WAITING_FOR_PROOF, PENDING, AWAITING_PROOF",
            "WAITING_FOR_PROOF, REJECTED, REJECTED",
            "WAITING_FOR_CHECK, PENDING, PROOF_SUBMITTED",
            "WAITING_FOR_CHECK, , PROOF_SUBMITTED",
            "PAID, VERIFIED, CONFIRMED",
            "RECEIPTED, VERIFIED, CONFIRMED"
    })
    void mapsTheInvoiceAndTheNewestProofToTheFrontendStatus(String invoice, String proof, PaymentStatus expected) {
        assertEquals(expected, PaymentStatus.fromInvoice(invoice, proof));
    }

    @Test
    void anUnknownInvoiceStatusCountsAsNotRequested() {
        assertEquals(PaymentStatus.NOT_REQUESTED, PaymentStatus.fromInvoice("SOMETHING_NEW", null));
    }
}
