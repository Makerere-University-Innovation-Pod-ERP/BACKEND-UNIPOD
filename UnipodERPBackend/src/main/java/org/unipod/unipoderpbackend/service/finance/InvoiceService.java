package org.unipod.unipoderpbackend.service.finance;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.unipod.unipoderpbackend.dto.finance.CreateInvoiceRequest;
import org.unipod.unipoderpbackend.dto.finance.FinanceSummaryResponse;
import org.unipod.unipoderpbackend.dto.finance.InvoiceResponse;
import org.unipod.unipoderpbackend.dto.finance.PaymentProofResponse;
import org.unipod.unipoderpbackend.dto.finance.ReceiptResponse;
import org.unipod.unipoderpbackend.exception.finance.InvalidInvoiceException;
import org.unipod.unipoderpbackend.exception.finance.InvoiceConflictException;
import org.unipod.unipoderpbackend.exception.finance.InvoiceNotFoundException;
import org.unipod.unipoderpbackend.exception.finance.ReceiptNotFoundException;
import org.unipod.unipoderpbackend.exception.storage.DocumentStorageException;
import org.unipod.unipoderpbackend.model.auth.ApiUser;
import org.unipod.unipoderpbackend.model.auth.ApiUserRole;
import org.unipod.unipoderpbackend.model.finance.Invoice;
import org.unipod.unipoderpbackend.model.finance.InvoicePaymentProof;
import org.unipod.unipoderpbackend.model.finance.InvoiceReceipt;
import org.unipod.unipoderpbackend.model.finance.InvoiceStatus;
import org.unipod.unipoderpbackend.model.notification.NotificationEvent;
import org.unipod.unipoderpbackend.model.notification.NotificationType;
import org.unipod.unipoderpbackend.repository.auth.ApiUserRepository;
import org.unipod.unipoderpbackend.repository.finance.InvoicePaymentProofRepository;
import org.unipod.unipoderpbackend.repository.finance.InvoiceReceiptRepository;
import org.unipod.unipoderpbackend.repository.finance.InvoiceRepository;
import org.unipod.unipoderpbackend.service.audit.AuditTrail;
import org.unipod.unipoderpbackend.service.notification.NotificationService;
import org.unipod.unipoderpbackend.service.storage.DocumentStorage;

@Service
public class InvoiceService {
    private static final ZoneId KAMPALA = ZoneId.of("Africa/Kampala");
    private static final Set<String> PROOF_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> PROOF_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp");
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
            .withZone(KAMPALA);

    private final InvoiceRepository invoices;
    private final InvoicePaymentProofRepository proofs;
    private final InvoiceReceiptRepository receipts;
    private final ApiUserRepository users;
    private final DocumentStorage storage;
    private final NotificationService notifications;
    private final AuditTrail auditTrail;
    private final Clock clock;
    private final long maxFileSize;
    private final TransactionTemplate transaction;

    public InvoiceService(
            InvoiceRepository invoices,
            InvoicePaymentProofRepository proofs,
            InvoiceReceiptRepository receipts,
            ApiUserRepository users,
            DocumentStorage storage,
            NotificationService notifications,
            AuditTrail auditTrail,
            Clock clock,
            PlatformTransactionManager transactionManager,
            @Value("${storage.s3.max-file-size}") long maxFileSize
    ) {
        if (maxFileSize <= 0) {
            throw new IllegalStateException("The storage file size limit must be greater than zero.");
        }
        this.invoices = invoices;
        this.proofs = proofs;
        this.receipts = receipts;
        this.users = users;
        this.storage = storage;
        this.notifications = notifications;
        this.auditTrail = auditTrail;
        this.clock = clock;
        this.maxFileSize = maxFileSize;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request, String actor) {
        Instant now = clock.instant();
        long sequence = invoices.nextInvoiceNumber();
        int year = LocalDate.ofInstant(now, KAMPALA).getYear();
        Invoice invoice = new Invoice(
                UUID.randomUUID(),
                "INV-%d-%03d".formatted(year, sequence),
                request.customerEmail(),
                clean(request.customerName()),
                clean(request.department()),
                request.description().trim(),
                request.amount(),
                request.sourceType(),
                clean(request.sourceId()),
                actor,
                now
        );
        invoices.saveAndFlush(invoice);
        auditTrail.record(actor, "invoice.created", "invoice", invoice.getId(), null,
                AuditTrail.details(
                        "number", invoice.getNumber(),
                        "amount", invoice.getAmount(),
                        "status", invoice.getStatus().name()
                ));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> forCustomer(String customerEmail, InvoiceStatus status, Pageable pageable) {
        Page<Invoice> page = status == null
                ? invoices.findAllByCustomerEmailIgnoreCaseOrderByCreatedAtDescIdDesc(customerEmail, pageable)
                : invoices.findAllByCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDescIdDesc(
                        customerEmail, status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse forCustomer(UUID id, String customerEmail) {
        return toResponse(invoices.findByIdAndCustomerEmailIgnoreCase(id, customerEmail)
                .orElseThrow(() -> new InvoiceNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> all(InvoiceStatus status, Pageable pageable) {
        Page<Invoice> page = status == null
                ? invoices.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : invoices.findAllByStatusOrderByCreatedAtDescIdDesc(status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID id) {
        return toResponse(invoices.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id)));
    }

    public InvoiceResponse uploadProof(
            UUID invoiceId,
            String reference,
            long amount,
            LocalDate paidOn,
            MultipartFile file,
            String actor,
            String ownerEmail
    ) {
        String cleanReference = clean(reference);
        if (cleanReference == null || cleanReference.length() > 120 || amount <= 0 || paidOn == null) {
            throw new InvalidInvoiceException("Payment proof details are invalid.");
        }
        if (file == null || file.isEmpty() || file.getSize() > maxFileSize) {
            throw new InvalidInvoiceException("The uploaded file is empty or exceeds the allowed size.");
        }
        String contentType = requireProofContentType(file.getContentType(), file.getOriginalFilename());
        String originalFilename = safeFilename(file.getOriginalFilename(), "payment-proof");
        transaction.execute(status -> {
            Invoice invoice = lockInvoice(invoiceId, ownerEmail);
            requireStatus(invoice, InvoiceStatus.WAITING_FOR_PROOF);
            return null;
        });

        String storedReference;
        try {
            storedReference = storage.upload(file);
        } catch (IOException exception) {
            throw new DocumentStorageException(exception);
        }

        try {
            return transaction.execute(status -> {
                Invoice invoice = lockInvoice(invoiceId, ownerEmail);
                requireStatus(invoice, InvoiceStatus.WAITING_FOR_PROOF);
                Instant now = clock.instant();
                InvoicePaymentProof proof = new InvoicePaymentProof(
                        UUID.randomUUID(),
                        invoiceId,
                        cleanReference,
                        amount,
                        paidOn,
                        storedReference,
                        contentType,
                        file.getSize(),
                        originalFilename,
                        actor,
                        now
                );
                proofs.saveAndFlush(proof);
                invoice.paymentProofUploaded(now);
                invoices.saveAndFlush(invoice);
                notifyFinance(invoice);
                auditTrail.record(actor, "invoice.payment_proof_uploaded", "invoice", invoiceId, null,
                        AuditTrail.details(
                                "reference", cleanReference,
                                "amount", amount,
                                "status", invoice.getStatus().name()
                        ));
                return toResponse(invoice, proof, null);
            });
        } catch (RuntimeException exception) {
            deleteAfterFailure(storedReference, exception);
            throw exception;
        }
    }

    @Transactional
    public InvoiceResponse verifyPayment(UUID invoiceId, UUID proofId, String actor) {
        Instant now = clock.instant();
        Invoice invoice = lockInvoice(invoiceId, null);
        requireStatus(invoice, InvoiceStatus.WAITING_FOR_CHECK);
        InvoicePaymentProof proof = latestProof(invoiceId);
        requireReviewedProof(proof, proofId);
        if (proof.getAmount() != invoice.getAmount()) {
            throw new InvoiceConflictException(
                    "The payment proof amount must match the invoice amount before it can be verified.");
        }
        proof.verify(actor, now);
        invoice.paymentVerified(now);
        proofs.saveAndFlush(proof);
        invoices.saveAndFlush(invoice);
        notifyCustomer(invoice, NotificationType.PAYMENT_CONFIRMED,
                "Payment confirmed for " + invoice.getNumber(),
                "Your payment of UGX " + invoice.getAmount() + " has been confirmed.");
        auditTrail.record(actor, "invoice.payment_verified", "invoice", invoiceId,
                AuditTrail.details("status", InvoiceStatus.WAITING_FOR_CHECK.name()),
                AuditTrail.details("status", invoice.getStatus().name()));
        return toResponse(invoice, proof, null);
    }

    @Transactional
    public InvoiceResponse rejectPayment(UUID invoiceId, UUID proofId, String reason, String actor) {
        String cleanReason = clean(reason);
        if (cleanReason == null || cleanReason.length() > 500) {
            throw new InvalidInvoiceException("A rejection reason is required.");
        }
        Instant now = clock.instant();
        Invoice invoice = lockInvoice(invoiceId, null);
        requireStatus(invoice, InvoiceStatus.WAITING_FOR_CHECK);
        InvoicePaymentProof proof = latestProof(invoiceId);
        requireReviewedProof(proof, proofId);
        proof.reject(actor, cleanReason, now);
        invoice.paymentRejected(now);
        proofs.saveAndFlush(proof);
        invoices.saveAndFlush(invoice);
        notifyCustomer(invoice, NotificationType.PAYMENT_REJECTED,
                "Payment proof rejected for " + invoice.getNumber(),
                "Your payment proof was rejected. Reason: " + cleanReason);
        auditTrail.record(actor, "invoice.payment_rejected", "invoice", invoiceId,
                AuditTrail.details("status", InvoiceStatus.WAITING_FOR_CHECK.name()),
                AuditTrail.details("status", invoice.getStatus().name(), "reason", cleanReason));
        return toResponse(invoice, proof, null);
    }

    @Transactional
    public InvoiceResponse issueReceipt(UUID invoiceId, String actor) {
        Instant now = clock.instant();
        Invoice invoice = lockInvoice(invoiceId, null);
        InvoiceReceipt existing = receipts.findByInvoiceId(invoiceId).orElse(null);
        if (existing != null) {
            return toResponse(invoice, latestProofOrNull(invoiceId), existing);
        }
        requireStatus(invoice, InvoiceStatus.PAID);
        int year = LocalDate.ofInstant(now, KAMPALA).getYear();
        InvoiceReceipt receipt = new InvoiceReceipt(
                UUID.randomUUID(),
                invoiceId,
                "RCP-%d-%03d".formatted(year, receipts.nextReceiptNumber()),
                actor,
                now
        );
        receipts.saveAndFlush(receipt);
        invoice.receipted(now);
        invoices.saveAndFlush(invoice);
        notifyCustomer(invoice, NotificationType.PAYMENT_CONFIRMED,
                "Receipt issued for " + invoice.getNumber(),
                "Receipt " + receipt.getReceiptNumber() + " has been issued for UGX " + invoice.getAmount() + ".");
        auditTrail.record(actor, "invoice.receipted", "invoice", invoiceId,
                AuditTrail.details("status", InvoiceStatus.PAID.name()),
                AuditTrail.details(
                        "status", invoice.getStatus().name(),
                        "receiptNumber", receipt.getReceiptNumber()
                ));
        return toResponse(invoice, latestProofOrNull(invoiceId), receipt);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(UUID invoiceId, String ownerEmail) {
        Invoice invoice = ownerEmail == null
                ? invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId))
                : invoices.findByIdAndCustomerEmailIgnoreCase(invoiceId, ownerEmail)
                        .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        return receipts.findByInvoiceId(invoice.getId())
                .map(ReceiptResponse::from)
                .orElseThrow(() -> new ReceiptNotFoundException(invoiceId));
    }

    @Transactional(readOnly = true)
    public DownloadedProof downloadProof(UUID invoiceId, String ownerEmail) {
        Invoice invoice = ownerEmail == null
                ? invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId))
                : invoices.findByIdAndCustomerEmailIgnoreCase(invoiceId, ownerEmail)
                        .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        InvoicePaymentProof proof = latestProof(invoice.getId());
        return new DownloadedProof(proof, storage.download(proof.getFileReference()));
    }

    @Transactional(readOnly = true)
    public ReceiptDocument downloadReceipt(UUID invoiceId, String ownerEmail) {
        Invoice invoice = ownerEmail == null
                ? invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId))
                : invoices.findByIdAndCustomerEmailIgnoreCase(invoiceId, ownerEmail)
                        .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        InvoiceReceipt receipt = receipts.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ReceiptNotFoundException(invoiceId));
        String content = """
                UNIPOD PAYMENT RECEIPT

                Receipt number: %s
                Invoice number: %s
                Customer: %s
                Description: %s
                Amount: %s %d
                Status: %s
                Issued by: %s
                Issued at: %s
                """.formatted(
                receipt.getReceiptNumber(),
                invoice.getNumber(),
                invoice.getCustomerName() == null ? invoice.getCustomerEmail() : invoice.getCustomerName(),
                invoice.getDescription(),
                invoice.getCurrency(),
                invoice.getAmount(),
                invoice.getStatus().label(),
                receipt.getIssuedBy(),
                RECEIPT_DATE.format(receipt.getIssuedAt())
        );
        return new ReceiptDocument(receipt.getReceiptNumber() + ".txt", content);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FinanceSummaryResponse summary() {
        long waitingForProof = invoices.countByStatus(InvoiceStatus.WAITING_FOR_PROOF);
        long waitingForCheck = invoices.countByStatus(InvoiceStatus.WAITING_FOR_CHECK);
        long paid = invoices.countByStatus(InvoiceStatus.PAID);
        long receipted = invoices.countByStatus(InvoiceStatus.RECEIPTED);
        long outstanding = invoices.sumAmountByStatusIn(
                List.of(InvoiceStatus.WAITING_FOR_PROOF, InvoiceStatus.WAITING_FOR_CHECK));
        long collected = invoices.sumAmountByStatusIn(List.of(InvoiceStatus.PAID, InvoiceStatus.RECEIPTED));
        return new FinanceSummaryResponse(
                waitingForProof + waitingForCheck + paid + receipted,
                waitingForProof,
                waitingForCheck,
                paid,
                receipted,
                outstanding,
                collected
        );
    }

    private Invoice lockInvoice(UUID invoiceId, String ownerEmail) {
        Invoice invoice = invoices.findByIdForUpdate(invoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (ownerEmail != null && !invoice.isOwnedBy(ownerEmail)) {
            throw new InvoiceNotFoundException(invoiceId);
        }
        return invoice;
    }

    private InvoicePaymentProof latestProof(UUID invoiceId) {
        return proofs.findFirstByInvoiceIdOrderByUploadedAtDescIdDesc(invoiceId)
                .orElseThrow(() -> new InvoiceConflictException(
                        "The invoice does not have payment proof to review."));
    }

    private static void requireReviewedProof(InvoicePaymentProof proof, UUID proofId) {
        if (proofId == null || !proof.getId().equals(proofId)) {
            throw new InvoiceConflictException(
                    "The payment proof changed while it was under review. Reload the invoice and try again.");
        }
    }

    private InvoicePaymentProof latestProofOrNull(UUID invoiceId) {
        return proofs.findFirstByInvoiceIdOrderByUploadedAtDescIdDesc(invoiceId).orElse(null);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return toResponse(invoice, latestProofOrNull(invoice.getId()),
                receipts.findByInvoiceId(invoice.getId()).orElse(null));
    }

    private static InvoiceResponse toResponse(
            Invoice invoice,
            InvoicePaymentProof proof,
            InvoiceReceipt receipt
    ) {
        return InvoiceResponse.from(
                invoice,
                proof == null ? null : PaymentProofResponse.from(proof),
                receipt == null ? null : ReceiptResponse.from(receipt)
        );
    }

    private void notifyFinance(Invoice invoice) {
        Set<String> recipients = users
                .findAllByRoleInAndEnabledTrue(List.of(ApiUserRole.FINANCE_OFFICER, ApiUserRole.ADMINISTRATOR))
                .stream()
                .map(ApiUser::getEmail)
                .collect(Collectors.toSet());
        if (recipients.isEmpty()) {
            return;
        }
        notifications.publish(new NotificationEvent(
                NotificationType.PAYMENT_PROOF_UPLOADED,
                recipients,
                "Payment proof uploaded for " + invoice.getNumber(),
                invoice.getCustomerEmail() + " uploaded payment proof of UGX " + invoice.getAmount() + ".",
                "invoice",
                invoice.getId()
        ));
    }

    private void notifyCustomer(Invoice invoice, NotificationType type, String title, String body) {
        notifications.publish(new NotificationEvent(
                type,
                Set.of(invoice.getCustomerEmail()),
                title,
                body,
                "invoice",
                invoice.getId()
        ));
    }

    private static void requireStatus(Invoice invoice, InvoiceStatus expected) {
        if (invoice.getStatus() != expected) {
            throw new InvoiceConflictException(
                    "The invoice cannot be changed from status " + invoice.getStatus() + ".");
        }
    }

    private static String requireProofContentType(String contentType, String originalFilename) {
        if (contentType == null || contentType.isBlank()) {
            if (hasProofExtension(originalFilename)) {
                return "application/octet-stream";
            }
            throw new InvalidInvoiceException("The uploaded file type is not supported.");
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int parameters = normalized.indexOf(';');
        if (parameters >= 0) {
            normalized = normalized.substring(0, parameters).trim();
        }
        if (PROOF_CONTENT_TYPES.contains(normalized)) {
            return normalized;
        }
        if ("application/octet-stream".equals(normalized) && hasProofExtension(originalFilename)) {
            return normalized;
        }
        throw new InvalidInvoiceException("The uploaded file type is not supported.");
    }

    private static boolean hasProofExtension(String filename) {
        if (filename == null) {
            return false;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return false;
        }
        return PROOF_EXTENSIONS.contains(filename.substring(dot + 1).trim().toLowerCase(Locale.ROOT));
    }

    private static String safeFilename(String originalFilename, String fallback) {
        String filename = originalFilename == null ? "" : originalFilename;
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (separator >= 0) {
            filename = filename.substring(separator + 1);
        }
        filename = filename.replaceAll("[\\p{Cntrl}]+", " ").trim();
        if (filename.isEmpty()) {
            return fallback;
        }
        return filename.length() <= 255 ? filename : filename.substring(0, 255);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void deleteAfterFailure(String reference, RuntimeException originalException) {
        try {
            storage.delete(reference);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    public record DownloadedProof(InvoicePaymentProof proof, InputStream content) {
    }

    public record ReceiptDocument(String filename, String content) {
    }
}
