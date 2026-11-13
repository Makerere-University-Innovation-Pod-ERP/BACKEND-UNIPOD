package org.unipod.unipoderpbackend.service.review;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;
import org.unipod.unipoderpbackend.model.review.ReviewSettings;
import org.unipod.unipoderpbackend.repository.review.ReviewAssignmentRepository;
import org.unipod.unipoderpbackend.repository.review.ReviewSettingsRepository;

/**
 * Read-only quorum contract for other modules. It performs no role or visibility check:
 * callers are expected to have authorised the request already.
 */
@Service
public class ReviewQuorumService {
    private final ReviewSettingsRepository settings;
    private final ReviewAssignmentRepository assignments;

    public ReviewQuorumService(ReviewSettingsRepository settings, ReviewAssignmentRepository assignments) {
        this.settings = settings;
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public ReviewQuorum quorum(UUID briefId) {
        return settings.findTopByBriefIdOrderByRoundDesc(briefId)
                .map(this::quorumFor)
                .orElseGet(ReviewQuorum::notConfigured);
    }

    ReviewQuorum quorumFor(ReviewSettings latest) {
        int required = latest.getReviewsNeeded();
        int completed = Math.toIntExact(assignments.countByBriefIdAndRoundAndStatus(
                latest.getBriefId(), latest.getRound(), ReviewAssignmentStatus.SUBMITTED));
        return new ReviewQuorum(true, latest.getRound(), required, completed, completed >= required);
    }
}
