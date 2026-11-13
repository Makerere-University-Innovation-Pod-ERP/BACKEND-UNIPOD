package org.unipod.unipoderpbackend.repository.finance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.unipod.unipoderpbackend.model.finance.InvoiceReceipt;

public interface InvoiceReceiptRepository extends JpaRepository<InvoiceReceipt, UUID> {
    Optional<InvoiceReceipt> findByInvoiceId(UUID invoiceId);

    @Query(value = "select nextval('receipt_number_seq')", nativeQuery = true)
    long nextReceiptNumber();
}
