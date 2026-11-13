package org.unipod.unipoderpbackend.repository.review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.review.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByAssignmentId(Long assignmentId);

    /** Every SUBMITTED assignment of one brief and round with its review, in one query. */
    @Query("""
            select new org.unipod.unipoderpbackend.repository.review.SubmittedReviewEntry(
                a.id, a.reviewerId, a.department, a.position, r.id, r.decision, r.comment, r.submittedAt)
            from ReviewAssignment a
            join Review r on r.assignmentId = a.id
            where a.briefId = :briefId
              and a.round = :round
              and a.status = org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus.SUBMITTED
            order by r.submittedAt asc, a.id asc
            """)
    List<SubmittedReviewEntry> findSubmittedForRound(@Param("briefId") UUID briefId, @Param("round") int round);
}
