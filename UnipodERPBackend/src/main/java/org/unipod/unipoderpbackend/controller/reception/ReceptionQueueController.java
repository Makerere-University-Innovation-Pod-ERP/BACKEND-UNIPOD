package org.unipod.unipoderpbackend.controller.reception;

import org.unipod.unipoderpbackend.dto.reception.ReceptionQueueResponse;
import org.unipod.unipoderpbackend.dto.submission.MissingDocsQueueResponse;
import org.unipod.unipoderpbackend.service.submission.SubmissionService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/queues")
@Validated
public class ReceptionQueueController {
    private final SubmissionService submissions;

    public ReceptionQueueController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @GetMapping("/reception")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public ReceptionQueueResponse reception(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return submissions.receptionQueue(
                PageRequest.of(page, size)
        );
    }

    /**
     * Briefs waiting on the submitter after reception requested more
     * information, with overdue flags and the last reminder time.
     */
    @GetMapping("/reception/missing-docs")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public MissingDocsQueueResponse missingDocs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
    ) {
        return submissions.missingDocsQueue(PageRequest.of(page, size));
    }
}
