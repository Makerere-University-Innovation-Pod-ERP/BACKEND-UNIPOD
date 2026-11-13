package org.unipod.unipoderpbackend.controller.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.finance.CreateInvoiceRequest;
import org.unipod.unipoderpbackend.dto.finance.InvoiceResponse;
import org.unipod.unipoderpbackend.dto.finance.RejectPaymentRequest;
import org.unipod.unipoderpbackend.model.finance.InvoiceStatus;
import org.unipod.unipoderpbackend.service.finance.InvoiceService;

@RestController
@RequestMapping("/invoices")
@Validated
public class InvoiceController {
    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public InvoiceResponse create(
            @Valid @RequestBody CreateInvoiceRequest request,
            Authentication authentication
    ) {
        return invoices.create(request, authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public PageResponse<InvoiceResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) InvoiceStatus status
    ) {
        return PageResponse.from(invoices.all(status, PageRequest.of(page, size)));
    }

    @PostMapping(path = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUBMITTER')")
    public InvoiceResponse uploadEvidence(
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

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public InvoiceResponse get(@PathVariable UUID id) {
        return invoices.get(id);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public InvoiceResponse verify(
            @PathVariable UUID id,
            @RequestParam UUID proofId,
            Authentication authentication
    ) {
        return invoices.verifyPayment(id, proofId, authentication.getName());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public InvoiceResponse reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectPaymentRequest request,
            Authentication authentication
    ) {
        return invoices.rejectPayment(id, request.proofId(), request.reason(), authentication.getName());
    }

    @PostMapping("/{id}/receipt")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public InvoiceResponse issueReceipt(@PathVariable UUID id, Authentication authentication) {
        return invoices.issueReceipt(id, authentication.getName());
    }

    @GetMapping("/{id}/proof/file")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public ResponseEntity<StreamingResponseBody> downloadProof(@PathVariable UUID id) {
        InvoiceService.DownloadedProof downloaded = invoices.downloadProof(id, null);
        return ResponseEntity.ok()
                .headers(MyInvoiceController.downloadHeaders(
                        downloaded.proof().getOriginalFilename(),
                        downloaded.proof().getContentType(),
                        downloaded.proof().getFileSize()
                ))
                .body(output -> {
                    try (var input = downloaded.content()) {
                        input.transferTo(output);
                    }
                });
    }

    @GetMapping("/{id}/receipt/download")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'FINANCE_OFFICER')")
    public ResponseEntity<String> downloadReceipt(@PathVariable UUID id) {
        return MyInvoiceController.receiptDownload(invoices.downloadReceipt(id, null));
    }
}
