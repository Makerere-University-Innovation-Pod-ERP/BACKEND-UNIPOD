package org.unipod.unipoderpbackend.controller.submission;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.unipod.unipoderpbackend.dto.PageResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmitterCapabilitiesResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmitterSubmissionResponse;
import org.unipod.unipoderpbackend.dto.submission.SubmitterTab;
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
            @RequestParam(required = false) String tab,
            Authentication authentication
    ) {
        return PageResponse.from(submissions.forSubmitter(
                authentication.getName(), parseTab(tab), PageRequest.of(page, size)
        ));
    }

    @GetMapping("/submissions/{id}")
    @PreAuthorize("hasRole('SUBMITTER')")
    public SubmitterSubmissionResponse submission(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return submissions.forSubmitter(id, authentication.getName());
    }

    @GetMapping("/capabilities")
    @PreAuthorize("hasRole('SUBMITTER')")
    public SubmitterCapabilitiesResponse capabilities() {
        return SubmitterCapabilitiesResponse.forSubmitter();
    }

    private static SubmitterTab parseTab(String value) {
        try {
            return SubmitterTab.from(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The tab must be BRIEFS or SERVICE_REQUESTS."
            );
        }
    }
}
