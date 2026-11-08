package org.unipod.unipoderpbackend.controller.submission;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmitterCapabilitiesResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmitterSubmissionResponse;
import org.unipod.unipoderpbackend.service.submission.SubmissionService;

@RestController
@RequestMapping("/me")
@Validated
public class MySubmissionsController {
    private final SubmissionService submissions;

    public MySubmissionsController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @GetMapping("/submissions")
    @PreAuthorize("hasRole('SUBMITTER')")
    public PageResponse<SubmitterSubmissionResponse> submissions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return PageResponse.from(submissions.forSubmitter(
                authentication.getName(), PageRequest.of(page, size)
        ));
    }

    @GetMapping("/capabilities")
    @PreAuthorize("hasRole('SUBMITTER')")
    public SubmitterCapabilitiesResponse capabilities(Authentication authentication) {
        return SubmitterCapabilitiesResponse.forSubmitter(authentication);
    }
}
