package org.unipod.unipoderpbackend.repository.review;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.review.ReviewFollowUpNote;

public interface ReviewFollowUpNoteRepository extends JpaRepository<ReviewFollowUpNote, Long> {
    List<ReviewFollowUpNote> findByReviewIdOrderByCreatedAtAsc(Long reviewId);
}
