package org.unipod.unipoderpbackend.controller.review;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.unipod.unipoderpbackend.dto.review.MyReviewResponse;
import org.unipod.unipoderpbackend.dto.review.ReviewSubmissionResponse;
import org.unipod.unipoderpbackend.dto.review.SubmitReviewRequest;
import org.unipod.unipoderpbackend.service.review.ReviewSubmissionService;

/** Reviewer-only endpoints: a reviewer submits and reads only their own review. */
@RestController
@RequestMapping("/cases/{id}")
public class ReviewSubmissionController {
    private static final String REVIEWER_ONLY = "hasRole('REVIEWER')";

    private final ReviewSubmissionService submissions;

    public ReviewSubmissionController(ReviewSubmissionService submissions) {
        this.submissions = submissions;
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(REVIEWER_ONLY)
    public ReviewSubmissionResponse submit(
            @PathVariable UUID id,
            @RequestBody SubmitReviewRequest request,
            Authentication authentication
    ) {
        return submissions.submit(id, request, authentication);
    }

    @GetMapping("/my-review")
    @PreAuthorize(REVIEWER_ONLY)
    public MyReviewResponse myReview(@PathVariable UUID id, Authentication authentication) {
        return submissions.myReview(id, authentication);
    }
}
