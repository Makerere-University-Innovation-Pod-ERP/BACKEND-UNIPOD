package org.unipod.unipoderpbackend.submissions;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select submission from Submission submission where submission.id = :id")
    Optional<Submission> findByIdForUpdate(@Param("id") UUID id);

    Page<Submission> findAllByOrderByUpdatedAtDescIdAsc(Pageable pageable);

    Page<Submission> findAllByRegisteredByOrderByUpdatedAtDescIdAsc(String registeredBy, Pageable pageable);

    Page<Submission> findAllByStageOrderByUpdatedAtDescIdAsc(SubmissionStage stage, Pageable pageable);

    @Query("""
            select submission
            from Submission submission
            where submission.code = :code
              and (
                  :scope = 'ALL'
                  or (:scope = 'SUBMITTER' and submission.registeredBy = :principal)
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
                  or (:scope = 'SUBMITTER' and submission.registeredBy = :principal)
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
