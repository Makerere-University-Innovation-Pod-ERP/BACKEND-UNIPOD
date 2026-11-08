package org.unipod.unipoderpbackend.controller.submission;

import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.submission.AdvanceSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.CaseFileResponse;
import org.unipod.unipoderpbackend.dto.submission.CreateDocumentRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.dto.submission.ForwardSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.service.submission.SubmissionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@RestController
@RequestMapping("/submissions")
@Validated
public class SubmissionController {
    private final SubmissionService submissions;

    public SubmissionController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public SubmissionResponse create(
            @Valid @RequestBody CreateSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.create(request, authentication.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public PageResponse<SubmissionResponse> all(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return PageResponse.from(submissions.all(pageable(page, size)));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public PageResponse<SubmissionResponse> mine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return PageResponse.from(submissions.mine(authentication.getName(), pageable(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public CaseFileResponse caseFile(@PathVariable UUID id) {
        return submissions.caseFile(id);
    }

    @PostMapping("/{id}/register")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public SubmissionResponse register(@PathVariable UUID id, Authentication authentication) {
        return submissions.register(id, authentication.getName());
    }

    @PostMapping("/{id}/forward")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public SubmissionResponse forward(
            @PathVariable UUID id,
            @Valid @RequestBody ForwardSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.forward(id, request.sectorId(), authentication.getName());
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public SubmissionResponse transition(
            @PathVariable UUID id,
            @Valid @RequestBody AdvanceSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.advance(
                id,
                SubmissionStage.valueOf(request.stage().name()),
                authentication.getName()
        );
    }

    @PostMapping("/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public DocumentResponse addDocument(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDocumentRequest request,
            Authentication authentication
    ) {
        return submissions.addDocument(id, request, authentication.getName());
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(page, size);
    }
}
