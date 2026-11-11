package org.unipod.unipoderpbackend.repository.review;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.review.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByAssignmentId(Long assignmentId);
}
