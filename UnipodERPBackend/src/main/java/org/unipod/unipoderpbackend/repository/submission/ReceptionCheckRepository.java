package org.unipod.unipoderpbackend.repository.submission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.submission.ReceptionCheck;

public interface ReceptionCheckRepository extends JpaRepository<ReceptionCheck, UUID> {
    List<ReceptionCheck> findAllBySubmissionIdOrderByItemKeyAsc(UUID submissionId);

    Optional<ReceptionCheck> findBySubmissionIdAndItemKey(UUID submissionId, String itemKey);
}
