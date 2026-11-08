package org.unipod.unipoderpbackend.controller.submission;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.Capabilities;
import org.unipod.unipoderpbackend.dto.submission.CaseFileAggregateResponse.TransitionOptions;
import org.unipod.unipoderpbackend.dto.submission.CaseTransitionRequest;
import org.unipod.unipoderpbackend.dto.submission.DocumentStatusUpdateRequest;
import org.unipod.unipoderpbackend.dto.submission.OutcomeUpdateRequest;
import org.unipod.unipoderpbackend.service.submission.CaseFileService;

@RestController
@RequestMapping("/cases/{id}")
public class CaseFileController {
    private static final String CASE_READ_ROLES =
            "hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'FACT_FINDING_OFFICER', "
                    + "'REVIEWER', 'SECTOR_OFFICER', 'IP_LIAISON', 'SUBMITTER', 'OBSERVER')";
    private static final String CASE_WRITE_ROLES =
            "hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR')";

    private final CaseFileService cases;

    public CaseFileController(CaseFileService cases) {
        this.cases = cases;
    }

    @GetMapping
    @PreAuthorize(CASE_READ_ROLES)
    public CaseFileAggregateResponse get(@PathVariable UUID id, Authentication authentication) {
        return cases.get(id, authentication);
    }

    @GetMapping("/capabilities")
    @PreAuthorize(CASE_READ_ROLES)
    public Capabilities capabilities(@PathVariable UUID id, Authentication authentication) {
        return cases.capabilities(id, authentication);
    }

    @GetMapping("/transitions/allowed")
    @PreAuthorize(CASE_READ_ROLES)
    public TransitionOptions allowedTransitions(@PathVariable UUID id, Authentication authentication) {
        return cases.allowedTransitions(id, authentication);
    }

    @PostMapping("/transitions")
    @PreAuthorize(CASE_WRITE_ROLES)
    public CaseFileAggregateResponse transition(
            @PathVariable UUID id,
            @Valid @RequestBody CaseTransitionRequest request,
            Authentication authentication
    ) {
        return cases.transition(id, request, authentication);
    }

    @PutMapping("/outcome")
    @PreAuthorize(CASE_WRITE_ROLES)
    public CaseFileAggregateResponse saveOutcome(
            @PathVariable UUID id,
            @Valid @RequestBody OutcomeUpdateRequest request,
            Authentication authentication
    ) {
        return cases.saveOutcome(id, request, authentication);
    }

    @PatchMapping("/documents/{documentId}")
    @PreAuthorize(CASE_WRITE_ROLES)
    public CaseFileAggregateResponse updateDocumentStatus(
            @PathVariable UUID id,
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentStatusUpdateRequest request,
            Authentication authentication
    ) {
        return cases.updateDocumentStatus(id, documentId, request, authentication);
    }
}
