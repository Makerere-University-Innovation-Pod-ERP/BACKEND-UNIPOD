package org.unipod.unipoderpbackend.repository.review;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;

public interface ReviewAssignmentRepository extends JpaRepository<ReviewAssignment, Long> {
    List<ReviewAssignment> findByBriefIdAndRoundOrderByPositionAsc(UUID briefId, int round);

    List<ReviewAssignment> findByReviewerIdAndStatus(String reviewerId, ReviewAssignmentStatus status);

    long countByBriefIdAndRoundAndStatus(UUID briefId, int round, ReviewAssignmentStatus status);
}
