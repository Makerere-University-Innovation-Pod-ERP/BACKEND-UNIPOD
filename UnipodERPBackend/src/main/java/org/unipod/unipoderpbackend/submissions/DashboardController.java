package org.unipod.unipoderpbackend.submissions;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {
    private final SubmissionService submissions;

    public DashboardController(SubmissionService submissions) {
        this.submissions = submissions;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('ADMINISTRATOR', 'INNOVATION_COORDINATOR', 'RECEPTIONIST', 'FACT_FINDING_OFFICER')")
    public DashboardOverviewResponse overview() {
        return submissions.overview();
    }
}
