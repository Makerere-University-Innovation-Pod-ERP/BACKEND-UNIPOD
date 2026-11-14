package org.unipod.unipoderpbackend.service.finance;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.dto.finance.InvoiceResponse;
import org.unipod.unipoderpbackend.dto.finance.ReceiptResponse;
import org.unipod.unipoderpbackend.service.document.DocumentBranding;
import org.unipod.unipoderpbackend.service.document.PdfDocument;

/**
 * PDF versions of the finance module's invoices and receipts. It only reads through
 * InvoiceService, so who may see which invoice stays decided there: pass the owner's
 * email for a submitter, or null for finance staff.
 */
@Service
public class FinanceDocumentService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy");

    private final InvoiceService invoices;
    private final DocumentBranding branding;
    private final Clock clock;

    public FinanceDocumentService(InvoiceService invoices, DocumentBranding branding, Clock clock) {
        this.invoices = invoices;
        this.branding = branding;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NamedPdf invoicePdf(UUID invoiceId, String ownerEmail) {
        InvoiceResponse invoice = ownerEmail == null ? invoices.get(invoiceId) : invoices.forCustomer(invoiceId, ownerEmail);
        PdfDocument pdf = new PdfDocument(branding, "Invoice " + invoice.number())
                .row("Invoice number", invoice.number())
                .row("Date issued", date(invoice.createdAt()))
                .row("Bill to", customer(invoice))
                .row("Email", invoice.customerEmail())
                .row("Department", valueOr(invoice.department(), "-"))
                .row("For", invoice.description())
                .row("Status", invoice.statusLabel())
                .emphasis("Total due", money(invoice))
                .heading("How to pay");
        for (String line : branding.bankDetails()) {
            pdf.paragraph(line);
        }
        pdf.paragraph("Use " + invoice.number() + " as the payment reference,")
                .paragraph("then upload your proof of payment in the UniPod portal.");
        return new NamedPdf("invoice-" + invoice.number() + ".pdf", pdf.finish());
    }

    @Transactional(readOnly = true)
    public NamedPdf receiptPdf(UUID invoiceId, String ownerEmail) {
        ReceiptResponse receipt = invoices.receipt(invoiceId, ownerEmail);
        InvoiceResponse invoice = ownerEmail == null ? invoices.get(invoiceId) : invoices.forCustomer(invoiceId, ownerEmail);
        byte[] bytes = new PdfDocument(branding, "Receipt " + receipt.receiptNumber())
                .row("Receipt number", receipt.receiptNumber())
                .row("Invoice number", invoice.number())
                .row("Date", date(receipt.issuedAt()))
                .row("Received from", customer(invoice))
                .row("Email", invoice.customerEmail())
                .row("Department", valueOr(invoice.department(), "-"))
                .row("For", invoice.description())
                .row("Issued by", receipt.issuedBy())
                .emphasis("Amount paid", money(invoice))
                .finish();
        return new NamedPdf("receipt-" + receipt.receiptNumber() + ".pdf", bytes);
    }

    private String date(Instant instant) {
        return DATE.format(LocalDate.ofInstant(instant, clock.getZone()));
    }

    private static String customer(InvoiceResponse invoice) {
        return valueOr(invoice.customerName(), invoice.customerEmail());
    }

    private static String money(InvoiceResponse invoice) {
        return "%s %,d".formatted(invoice.currency(), invoice.amount());
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record NamedPdf(String fileName, byte[] content) {
    }
}
