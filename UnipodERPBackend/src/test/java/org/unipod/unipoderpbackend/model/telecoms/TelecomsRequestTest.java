package org.unipod.unipoderpbackend.model.telecoms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.unipod.unipoderpbackend.dto.telecoms.TelecomsRequestPayload;
import org.unipod.unipoderpbackend.exception.operations.OperationsConflictException;

class TelecomsRequestTest {
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    void submittingDraftChangesItsStatusAndTimestamp() {
        TelecomsRequestPayload payload = payload(true);
        TelecomsRequest request = new TelecomsRequest(
                UUID.randomUUID(), "TL-2026-000001", payload, "owner@example.test",
                TelecomsRequestStatus.DRAFT, null, NOW);

        request.submitDraft(payload, null, NOW.plusSeconds(60));

        assertEquals(TelecomsRequestStatus.NEW, request.getStatus());
        assertEquals(NOW.plusSeconds(60), request.getUpdatedAt());
    }

    @Test
    void quoteMustBeSentBeforeSchedulingAndPaymentMustBeConfirmed() {
        TelecomsRequest request = new TelecomsRequest(
                UUID.randomUUID(), "TL-2026-000002", payload(false), "owner@example.test",
                TelecomsRequestStatus.NEW, null, NOW);
        request.sendQuote(null, "{\"amount\":1000}", NOW.plusSeconds(60));

        assertEquals(TelecomsPaymentStatus.AWAITING_PROOF, request.getPaymentStatus());
        OperationsConflictException exception = assertThrows(
                OperationsConflictException.class,
                () -> request.moveTo(TelecomsRequestStatus.SCHEDULED, NOW.plusSeconds(120)));

        assertEquals("PAYMENT_REQUIRED", exception.getCode());
        assertEquals(TelecomsRequestStatus.QUOTED, request.getStatus());
    }

    @Test
    void confirmedPaymentAllowsScheduling() {
        TelecomsRequest request = new TelecomsRequest(
                UUID.randomUUID(), "TL-2026-000003", payload(false), "owner@example.test",
                TelecomsRequestStatus.NEW, null, NOW);
        request.sendQuote(null, "{\"amount\":1000}", NOW.plusSeconds(60));

        request.confirmPayment("PAY-1000", NOW.plusSeconds(120));
        request.moveTo(TelecomsRequestStatus.SCHEDULED, NOW.plusSeconds(180));

        assertEquals(TelecomsPaymentStatus.CONFIRMED, request.getPaymentStatus());
        assertEquals("PAY-1000", request.getPaymentReference());
        assertNotNull(request.getPaymentConfirmedAt());
        assertEquals(TelecomsRequestStatus.SCHEDULED, request.getStatus());
    }

    @Test
    void returnedRequestCanBeEditedAndResubmitted() {
        TelecomsRequest request = new TelecomsRequest(
                UUID.randomUUID(), "TL-2026-000004", payload(false), "owner@example.test",
                TelecomsRequestStatus.NEW, null, NOW);
        request.sendBack(NOW.plusSeconds(60));

        request.updateDraft(payload(true), null, NOW.plusSeconds(120));
        request.submitDraft(payload(false), null, NOW.plusSeconds(180));

        assertEquals(TelecomsRequestStatus.NEW, request.getStatus());
    }

    @Test
    void requestCannotSkipDirectlyFromNewToInProgress() {
        TelecomsRequest request = new TelecomsRequest(
                UUID.randomUUID(), "TL-2026-000003", payload(false), "owner@example.test",
                TelecomsRequestStatus.NEW, null, NOW);

        assertThrows(
                OperationsConflictException.class,
                () -> request.moveTo(TelecomsRequestStatus.IN_PROGRESS, NOW.plusSeconds(60)));
        assertEquals(TelecomsRequestStatus.NEW, request.getStatus());
    }

    private static TelecomsRequestPayload payload(boolean draft) {
        return new TelecomsRequestPayload(
                "Customer",
                "Student",
                "RF testing",
                "Radio prototype",
                null,
                true,
                null,
                draft
        );
    }
}
