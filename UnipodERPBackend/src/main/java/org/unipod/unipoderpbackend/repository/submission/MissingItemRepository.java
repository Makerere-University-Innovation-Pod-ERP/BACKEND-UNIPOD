package org.unipod.unipoderpbackend.repository.submission;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.submission.DocumentType;
import org.unipod.unipoderpbackend.model.submission.MissingItem;
import org.unipod.unipoderpbackend.model.submission.MissingItemKind;
import org.unipod.unipoderpbackend.model.submission.MissingItemStatus;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

public interface MissingItemRepository extends JpaRepository<MissingItem, UUID> {
    List<MissingItem> findAllBySubmissionIdOrderByRequestedAtAscIdAsc(UUID submissionId);

    List<MissingItem> findAllBySubmissionIdAndStatusOrderByRequestedAtAscIdAsc(
            UUID submissionId,
            MissingItemStatus status
    );

    List<MissingItem> findAllBySubmissionIdInAndStatusOrderByRequestedAtAscIdAsc(
            Collection<UUID> submissionIds,
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

    @Query("""
            select count(distinct item.submissionId)
            from MissingItem item
            where item.status = :status
              and item.submissionId in (
                  select submission.id
                  from Submission submission
                  where submission.submitterId = :submitterId
                    and submission.stage in :stages
              )
            """)
    long countSubmissionsWithOpenItems(
            @Param("submitterId") String submitterId,
            @Param("status") MissingItemStatus status,
            @Param("stages") Collection<SubmissionStage> stages
    );

    @Query("""
            select count(distinct item.submissionId)
            from MissingItem item
            where item.status = :status
              and item.dueAt < :now
              and item.submissionId in (
                  select submission.id
                  from Submission submission
                  where submission.stage = :stage
              )
            """)
    long countOverdueSubmissions(
            @Param("stage") SubmissionStage stage,
            @Param("status") MissingItemStatus status,
            @Param("now") Instant now
    );
}
