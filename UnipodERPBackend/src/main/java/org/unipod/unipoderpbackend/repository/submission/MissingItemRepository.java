package org.unipod.unipoderpbackend.repository.submission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemKind;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;

public interface MissingItemRepository extends JpaRepository<MissingItem, UUID> {
    List<MissingItem> findAllBySubmissionIdOrderByRequestedAtAscIdAsc(UUID submissionId);

    List<MissingItem> findAllBySubmissionIdAndStatusOrderByRequestedAtAscIdAsc(
            UUID submissionId,
            MissingItemStatus status
    );

    List<MissingItem> findAllBySubmissionIdAndKindAndStatusOrderByRequestedAtAscIdAsc(
            UUID submissionId,
            MissingItemKind kind,
            MissingItemStatus status
    );

    List<MissingItem> findAllBySubmissionIdAndKindAndDocumentTypeAndStatus(
            UUID submissionId,
            MissingItemKind kind,
            DocumentType documentType,
            MissingItemStatus status
    );

    Optional<MissingItem> findByIdAndSubmissionId(UUID id, UUID submissionId);

    long countBySubmissionIdAndStatus(UUID submissionId, MissingItemStatus status);
}
