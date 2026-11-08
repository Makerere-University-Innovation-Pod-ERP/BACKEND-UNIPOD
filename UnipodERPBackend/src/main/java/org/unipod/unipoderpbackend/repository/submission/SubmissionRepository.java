package org.unipod.unipoderpbackend.repository.submission;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.submission.Submission;
import org.unipod.unipoderpbackend.model.submission.SubmissionCategory;
import org.unipod.unipoderpbackend.model.submission.SubmissionStage;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "responsibleSector")
    @Query("select submission from Submission submission where submission.id = :id")
    Optional<Submission> findByIdForUpdate(@Param("id") UUID id);

    @Override
    @EntityGraph(attributePaths = "responsibleSector")
    Optional<Submission> findById(UUID id);

    @EntityGraph(attributePaths = "responsibleSector")
    @Query("""
            select submission
            from Submission submission
            where submission.id = :id
              and (
                  :scope = 'ALL'
                  or (:scope = 'SUBMITTER' and submission.submitterId = :principal)
                  or (:scope in ('REVIEWER', 'REVIEWER_OR_SECTOR_OFFICER')
                      and submission.assignedReviewerSubject = :principal)
                  or (:scope in ('SECTOR_OFFICER', 'REVIEWER_OR_SECTOR_OFFICER') and exists (
                      select sectorMember.id from SectorMember sectorMember
                      where sectorMember.sectorId = submission.responsibleSectorId
                        and sectorMember.subject = :principal
                  ))
              )
            """)
    Optional<Submission> findVisibleById(
            @Param("id") UUID id,
            @Param("scope") String scope,
            @Param("principal") String principal
    );

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllByOrderByUpdatedAtDescIdAsc(Pageable pageable);

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllByRegisteredByOrderByUpdatedAtDescIdAsc(String registeredBy, Pageable pageable);

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllBySubmitterIdOrderByUpdatedAtDescIdAsc(String submitterId, Pageable pageable);

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllByStageOrderByUpdatedAtDescIdAsc(SubmissionStage stage, Pageable pageable);

    @EntityGraph(attributePaths = "responsibleSector")
    @Query("""
            select submission
            from Submission submission
            where submission.code = :code
              and (
                  :scope = 'ALL'
                  or (:scope = 'SUBMITTER' and submission.submitterId = :principal)
                  or (:scope in ('REVIEWER', 'REVIEWER_OR_SECTOR_OFFICER')
                      and submission.assignedReviewerSubject = :principal)
                  or (:scope in ('SECTOR_OFFICER', 'REVIEWER_OR_SECTOR_OFFICER') and exists (
                      select sectorMember.id from SectorMember sectorMember
                      where sectorMember.sectorId = submission.responsibleSectorId
                        and sectorMember.subject = :principal
                  ))
              )
            """)
    Optional<Submission> findVisibleByCode(
            @Param("code") String code,
            @Param("scope") String scope,
            @Param("principal") String principal
    );

    @EntityGraph(attributePaths = "responsibleSector")
    @Query("""
            select submission
            from Submission submission
            where (:category is null or submission.category = :category)
              and (:stage is null or submission.stage = :stage)
              and (:sectorId is null or submission.responsibleSectorId = :sectorId)
              and (
                  coalesce(:titleQuery, '') = ''
                  or lower(submission.title) like lower(concat('%', coalesce(:titleQuery, ''), '%'))
              )
              and (
                  :scope = 'ALL'
                  or (:scope = 'SUBMITTER' and submission.submitterId = :principal)
                  or (:scope in ('REVIEWER', 'REVIEWER_OR_SECTOR_OFFICER')
                      and submission.assignedReviewerSubject = :principal)
                  or (:scope in ('SECTOR_OFFICER', 'REVIEWER_OR_SECTOR_OFFICER') and exists (
                      select sectorMember.id from SectorMember sectorMember
                      where sectorMember.sectorId = submission.responsibleSectorId
                        and sectorMember.subject = :principal
                  ))
              )
            order by case when submission.receivedAt is null then 1 else 0 end,
                     submission.receivedAt desc,
                     submission.id asc
            """)
    Page<Submission> searchVisible(
            @Param("category") SubmissionCategory category,
            @Param("stage") SubmissionStage stage,
            @Param("sectorId") UUID sectorId,
            @Param("titleQuery") String titleQuery,
            @Param("scope") String scope,
            @Param("principal") String principal,
            Pageable pageable
    );
}
