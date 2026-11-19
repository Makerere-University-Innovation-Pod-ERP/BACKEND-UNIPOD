package org.unipod.unipoderpbackend.repository.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.operations.PaymentStatus;
import org.unipod.unipoderpbackend.support.OperationsTestData;

@SpringBootTest
@Transactional
class PaymentLinkQueryTest {
    private static final Instant T0 = Instant.parse("2026-11-01T08:00:00Z");

    @Autowired
    private PaymentLinkQuery query;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aSourceWithoutAnInvoiceIsAbsent() {
        String unknown = UUID.randomUUID().toString();

        assertTrue(query.bySource("BOOKING", List.of(unknown)).isEmpty());
        assertTrue(query.bySource("BOOKING", List.of()).isEmpty());
    }

    @Test
    void readsTheStatusOfEachLinkedInvoiceInOneQuery() {
        String waiting = UUID.randomUUID().toString();
        String checking = UUID.randomUUID().toString();
        String paid = UUID.randomUUID().toString();
        OperationsTestData.invoice(jdbcTemplate, "BOOKING", waiting, "a@test.local", "WAITING_FOR_PROOF", T0);
        OperationsTestData.invoice(jdbcTemplate, "BOOKING", checking, "a@test.local", "WAITING_FOR_CHECK", T0);
        OperationsTestData.invoice(jdbcTemplate, "BOOKING", paid, "a@test.local", "RECEIPTED", T0);

        Map<String, PaymentStatus> statuses = query.bySource("BOOKING", List.of(waiting, checking, paid));

        assertEquals(PaymentStatus.AWAITING_PROOF, statuses.get(waiting));
        assertEquals(PaymentStatus.PROOF_SUBMITTED, statuses.get(checking));
        assertEquals(PaymentStatus.CONFIRMED, statuses.get(paid));
    }

    @Test
    void aRejectedNewestProofOnAnInvoiceWaitingForProofIsRejected() {
        String source = UUID.randomUUID().toString();
        UUID invoice = OperationsTestData.invoice(jdbcTemplate, "BOOKING", source, "a@test.local",
                "WAITING_FOR_PROOF", T0);
        OperationsTestData.proof(jdbcTemplate, invoice, "REJECTED", T0.plusSeconds(60));

        assertEquals(PaymentStatus.REJECTED, query.bySource("BOOKING", List.of(source)).get(source));
    }

    @Test
    void anOlderRejectedProofDoesNotHideANewPendingOne() {
        String source = UUID.randomUUID().toString();
        UUID invoice = OperationsTestData.invoice(jdbcTemplate, "BOOKING", source, "a@test.local",
                "WAITING_FOR_CHECK", T0);
        OperationsTestData.proof(jdbcTemplate, invoice, "REJECTED", T0.plusSeconds(60));
        OperationsTestData.proof(jdbcTemplate, invoice, "PENDING", T0.plusSeconds(120));

        assertEquals(PaymentStatus.PROOF_SUBMITTED, query.bySource("BOOKING", List.of(source)).get(source));
    }

    @Test
    void theNewestInvoiceForASourceWins() {
        String source = UUID.randomUUID().toString();
        OperationsTestData.invoice(jdbcTemplate, "BOOKING", source, "a@test.local", "WAITING_FOR_PROOF", T0);
        OperationsTestData.invoice(jdbcTemplate, "BOOKING", source, "a@test.local", "PAID", T0.plusSeconds(600));

        assertEquals(PaymentStatus.CONFIRMED, query.bySource("BOOKING", List.of(source)).get(source));
    }

    @Test
    void doesNotMixSourceTypes() {
        String source = UUID.randomUUID().toString();
        OperationsTestData.invoice(jdbcTemplate, "SESSION", source, "a@test.local", "PAID", T0);

        assertTrue(query.bySource("BOOKING", List.of(source)).isEmpty());
    }
}
