package org.unipod.unipoderpbackend.repository.submission;

import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubmissionDocumentRepository extends JpaRepository<SubmissionDocument, UUID> {
    List<SubmissionDocument> findAllBySubmissionIdOrderByCreatedAtAscIdAsc(UUID submissionId);
}
