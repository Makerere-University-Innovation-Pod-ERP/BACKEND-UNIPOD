package org.unipod.unipoderpbackend.repository.finance;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.finance.InvoicePaymentProof;

public interface InvoicePaymentProofRepository extends JpaRepository<InvoicePaymentProof, UUID> {
    Optional<InvoicePaymentProof> findFirstByInvoiceIdOrderByUploadedAtDescIdDesc(UUID invoiceId);

    Optional<InvoicePaymentProof> findByIdAndInvoiceId(UUID id, UUID invoiceId);
}
