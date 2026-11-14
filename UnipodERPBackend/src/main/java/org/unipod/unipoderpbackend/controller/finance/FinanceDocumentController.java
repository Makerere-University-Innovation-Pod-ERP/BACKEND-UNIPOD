package org.unipod.unipoderpbackend.controller.finance;

import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.service.finance.FinanceDocumentService;

@RestController
public class FinanceDocumentController {
    private final FinanceDocumentService documents;

    public FinanceDocumentController(FinanceDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/invoices/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public ResponseEntity<byte[]> invoice(@PathVariable UUID id) {
        return pdf(documents.invoicePdf(id, null));
    }

    @GetMapping("/invoices/{id}/receipt/pdf")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
        return pdf(documents.receiptPdf(id, null));
    }

    @GetMapping("/me/invoices/{id}/pdf")
    @PreAuthorize("hasRole('SUBMITTER')")
    public ResponseEntity<byte[]> myInvoice(@PathVariable UUID id, Authentication authentication) {
        return pdf(documents.invoicePdf(id, authentication.getName()));
    }

    @GetMapping("/me/invoices/{id}/receipt/pdf")
    @PreAuthorize("hasRole('SUBMITTER')")
    public ResponseEntity<byte[]> myReceipt(@PathVariable UUID id, Authentication authentication) {
        return pdf(documents.receiptPdf(id, authentication.getName()));
    }

    private static ResponseEntity<byte[]> pdf(FinanceDocumentService.NamedPdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(pdf.fileName()).build().toString())
                .body(pdf.content());
    }
}
