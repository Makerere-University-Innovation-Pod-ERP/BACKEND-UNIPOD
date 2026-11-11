package org.unipod.unipoderpbackend.controller.submission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.submission.AdvanceSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.CaseFileResponse;
import org.unipod.unipoderpbackend.dto.submission.CreateDocumentRequest;
import org.unipod.unipoderpbackend.dto.submission.CreateSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentResponse;
import org.unipod.unipoderpbackend.dto.submission.ForwardSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.ReviewerAssignmentRequest;
import org.unipod.unipoderpbackend.dto.submission.RequestInformationRequest;
import org.unipod.unipoderpbackend.dto.submission.SubmissionResponse;
import org.unipod.unipoderpbackend.exception.submission.InvalidSubmissionException;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;
import org.unipod.unipoderpbackend.security.SubmitterAuthorization;
import org.unipod.unipoderpbackend.service.submission.SubmissionService;

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
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public SubmissionResponse create(
            @Valid @RequestBody CreateSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.create(request, authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public SubmissionResponse updateDraft(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSubmissionRequest request,
            Authentication authentication
    ) {
        return submissions.updateDraft(id, request, authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'IP_LIAISON', 'OBSERVER', 'SUBMITTER', 'REVIEWER', 'SECTOR_OFFICER')")
    public PageResponse<SubmissionResponse> all(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) SubmissionStage stage,
            @RequestParam(required = false) UUID sector,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receivedTo,
            Authentication authentication
    ) {
        return PageResponse.from(submissions.search(
                parseCategory(category), stage, sector, q, receivedFrom, receivedTo,
                pageable(page, size), authentication
        ));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'IP_LIAISON', 'OBSERVER', 'SUBMITTER', 'REVIEWER', 'SECTOR_OFFICER')")
    public PageResponse<SubmissionResponse> search(
            @RequestParam @NotBlank String code,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return PageResponse.from(submissions.searchByCodeOrTitle(code, pageable(page, size), authentication));
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

    @PostMapping("/{id}/request-information")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public SubmissionResponse requestInformation(
            @PathVariable UUID id,
            @Valid @RequestBody RequestInformationRequest request,
            Authentication authentication
    ) {
        return submissions.requestInformation(id, request, authentication.getName());
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR')")
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
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER', 'SUBMITTER')")
    public DocumentResponse addDocument(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDocumentRequest request,
            Authentication authentication
    ) {
        return submissions.addDocument(id, request, authentication.getName(),
                SubmitterAuthorization.hasSubmitterRole(authentication));
    }

    @PutMapping("/{id}/reviewer")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public SubmissionResponse assignReviewer(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewerAssignmentRequest request
    ) {
        return submissions.assignReviewer(id, request.subject());
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(page, size);
    }

    private static SubmissionCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SubmissionCategory.fromJson(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidSubmissionException("The category is invalid.");
        }
    }

}
