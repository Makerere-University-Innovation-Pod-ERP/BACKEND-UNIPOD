package org.unipod.unipoderpbackend.controller.submission;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.submission.AssistedSubmissionRequest;
import org.unipod.unipoderpbackend.dto.submission.AssistedSubmissionResponse;
import org.unipod.unipoderpbackend.service.submission.AssistedSubmissionService;

@RestController
@RequestMapping("/submissions")
@Validated
public class AssistedSubmissionController {
    private final AssistedSubmissionService assistedSubmissions;

    public AssistedSubmissionController(AssistedSubmissionService assistedSubmissions) {
        this.assistedSubmissions = assistedSubmissions;
    }

    @PostMapping("/assisted")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public AssistedSubmissionResponse create(
            @Valid @RequestBody AssistedSubmissionRequest request,
            Authentication authentication
    ) {
        return assistedSubmissions.create(request, authentication.getName());
    }
}
