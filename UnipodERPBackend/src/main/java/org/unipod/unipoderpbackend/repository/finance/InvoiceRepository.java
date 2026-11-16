package org.unipod.unipoderpbackend.repository.finance;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.finance.Invoice;
import org.unipod.unipoderpbackend.model.finance.InvoiceStatus;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from Invoice invoice where invoice.id = :id")
    Optional<Invoice> findByIdForUpdate(@Param("id") UUID id);

    Optional<Invoice> findByIdAndCustomerEmailIgnoreCase(UUID id, String customerEmail);

    Page<Invoice> findAllByCustomerEmailIgnoreCaseOrderByCreatedAtDescIdDesc(
            String customerEmail,
            Pageable pageable
    );

    Page<Invoice> findAllByCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDescIdDesc(
            String customerEmail,
            InvoiceStatus status,
            Pageable pageable
    );

    Page<Invoice> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Invoice> findAllByStatusOrderByCreatedAtDescIdDesc(InvoiceStatus status, Pageable pageable);

    long countByStatus(InvoiceStatus status);

    long countByCustomerEmailIgnoreCaseAndStatusIn(
            String customerEmail,
            Collection<InvoiceStatus> statuses
    );

    @Query("select coalesce(sum(invoice.amount), 0) from Invoice invoice where invoice.status in :statuses")
    long sumAmountByStatusIn(@Param("statuses") Collection<InvoiceStatus> statuses);

    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceNumber();
}
