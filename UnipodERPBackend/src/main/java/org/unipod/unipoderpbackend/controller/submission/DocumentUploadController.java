package org.unipod.unipoderpbackend.controller.submission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.model.submission.DocumentStatus;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.security.SubmitterAuthorization;
import org.unipod.unipoderpbackend.service.submission.UploadedDocumentService;

@RestController
@RequestMapping("/submissions/{id}/documents")
@Validated
public class DocumentUploadController {
    private final UploadedDocumentService documents;

    public DocumentUploadController(UploadedDocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public DocumentResponse upload(
            @PathVariable UUID id,
            @RequestParam("name") @NotBlank @Size(max = 255) String name,
            @RequestParam("status") @NotNull DocumentStatus status,
            @RequestParam(name = "documentType", defaultValue = "OTHER") @NotNull DocumentType documentType,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        return documents.upload(
                id,
                name,
                documentType,
                status,
                file,
                authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication)
        );
    }

    @GetMapping("/{documentId}/file")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable UUID id,
            @PathVariable UUID documentId
    ) {
        UploadedDocumentService.DownloadedDocument downloaded = documents.download(id, documentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String filename = downloaded.document().getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = downloaded.document().getName();
        }
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build());
        headers.set("X-Content-Type-Options", "nosniff");
        if (downloaded.document().getFileSize() != null) {
            headers.setContentLength(downloaded.document().getFileSize());
        }
        return ResponseEntity.ok()
                .headers(headers)
                .body(output -> {
                    try (var input = downloaded.content()) {
                        input.transferTo(output);
                    }
                });
    }

}
