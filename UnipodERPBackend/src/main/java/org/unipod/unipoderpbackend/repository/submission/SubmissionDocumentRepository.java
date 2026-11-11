package org.unipod.unipoderpbackend.repository.submission;

import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.SubmissionDocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionDocumentRepository extends JpaRepository<SubmissionDocument, UUID> {
    List<SubmissionDocument> findAllBySubmissionIdOrderByCreatedAtAscIdAsc(UUID submissionId);

    Optional<SubmissionDocument> findByIdAndSubmissionId(UUID id, UUID submissionId);

    /**
     * The highest revision number already recorded for a document type on a
     * submission. Revision numbering starts at 1 for the first document.
     */
    @Query("""
            select coalesce(max(document.version), 0)
            from SubmissionDocument document
            where document.submissionId = :submissionId
              and document.documentType = :documentType
            """)
    long maxVersion(
            @Param("submissionId") UUID submissionId,
            @Param("documentType") DocumentType documentType
    );
}
