package org.unipod.unipoderpbackend.submissions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionDocumentRepository extends JpaRepository<SubmissionDocument, UUID> {
    List<SubmissionDocument> findAllBySubmissionIdOrderByCreatedAtAscIdAsc(UUID submissionId);

    Optional<SubmissionDocument> findByIdAndSubmissionId(UUID id, UUID submissionId);
}
