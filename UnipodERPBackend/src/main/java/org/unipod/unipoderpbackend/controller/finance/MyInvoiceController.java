package org.unipod.unipoderpbackend.controller.finance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.finance.InvoiceResponse;
import org.unipod.unipoderpbackend.dto.finance.ReceiptResponse;
import org.unipod.unipoderpbackend.model.finance.InvoiceStatus;
import org.unipod.unipoderpbackend.service.finance.InvoiceService;

@RestController
@RequestMapping("/me/invoices")
@Validated
public class MyInvoiceController {
    private final InvoiceService invoices;

    public MyInvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUBMITTER')")
    public PageResponse<InvoiceResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) InvoiceStatus status,
            Authentication authentication
    ) {
        return PageResponse.from(invoices.forCustomer(
                authentication.getName(), status, PageRequest.of(page, size)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUBMITTER')")
    public InvoiceResponse get(@PathVariable UUID id, Authentication authentication) {
        return invoices.forCustomer(id, authentication.getName());
    }

    @PostMapping(path = "/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUBMITTER')")
    public InvoiceResponse uploadProof(
            @PathVariable UUID id,
            @RequestParam("reference") @NotBlank @Size(max = 120) String reference,
            @RequestParam("amount") @Positive long amount,
            @RequestParam("paidOn") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidOn,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return invoices.uploadProof(
                id, reference, amount, paidOn, file, authentication.getName(), authentication.getName()
        );
    }

    @GetMapping("/{id}/proof/file")
    @PreAuthorize("hasRole('SUBMITTER')")
    public ResponseEntity<StreamingResponseBody> downloadProof(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        InvoiceService.DownloadedProof downloaded = invoices.downloadProof(id, authentication.getName());
        HttpHeaders headers = downloadHeaders(
                downloaded.proof().getOriginalFilename(),
                downloaded.proof().getContentType(),
                downloaded.proof().getFileSize()
        );
        return ResponseEntity.ok()
                .headers(headers)
                .body(output -> {
                    try (var input = downloaded.content()) {
                        input.transferTo(output);
                    }
                });
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasRole('SUBMITTER')")
    public ReceiptResponse receipt(@PathVariable UUID id, Authentication authentication) {
        return invoices.receipt(id, authentication.getName());
    }

    @GetMapping("/{id}/receipt/download")
    @PreAuthorize("hasRole('SUBMITTER')")
    public ResponseEntity<String> downloadReceipt(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return receiptDownload(invoices.downloadReceipt(id, authentication.getName()));
    }

    static HttpHeaders downloadHeaders(String filename, String contentType, long size) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String attachmentName = filename == null || filename.isBlank() ? "payment-proof" : filename;
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(attachmentName, StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        if (size > 0) {
            headers.setContentLength(size);
        }
        return headers;
    }

    static ResponseEntity<String> receiptDownload(InvoiceService.ReceiptDocument document) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(document.filename(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(document.content());
    }
}
