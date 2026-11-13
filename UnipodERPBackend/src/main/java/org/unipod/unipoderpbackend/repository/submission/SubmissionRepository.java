package org.unipod.unipoderpbackend.repository.submission;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
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
              and """ + SubmissionVisibilityPredicate.QUERY)
    Optional<Submission> findVisibleById(
            @Param("id") UUID id,
            @Param("scope") String scope,
            @Param("principal") String principal
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "responsibleSector")
    @Query("""
            select submission
            from Submission submission
            where submission.id = :id
              and """ + SubmissionVisibilityPredicate.QUERY)
    Optional<Submission> findVisibleByIdForUpdate(
            @Param("id") UUID id,
            @Param("scope") String scope,
            @Param("principal") String principal
    );

    @EntityGraph(attributePaths = "responsibleSector")
    Optional<Submission> findByIdAndSubmitterId(UUID id, String submitterId);

    boolean existsByProjectCode(String projectCode);

    long countBySubmitterId(String submitterId);

    long countBySubmitterIdAndCategoryNot(String submitterId, SubmissionCategory category);

    long countBySubmitterIdAndCategory(String submitterId, SubmissionCategory category);

    long countBySubmitterIdAndStage(String submitterId, SubmissionStage stage);

    long countBySubmitterIdAndStageIn(String submitterId, Collection<SubmissionStage> stages);

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllByOrderByUpdatedAtDescIdAsc(Pageable pageable);

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllByRegisteredByOrderByUpdatedAtDescIdAsc(String registeredBy, Pageable pageable);

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllBySubmitterIdOrderByUpdatedAtDescIdAsc(String submitterId, Pageable pageable);

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllBySubmitterIdAndCategoryNotOrderByUpdatedAtDescIdAsc(
            String submitterId,
            SubmissionCategory category,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllBySubmitterIdAndCategoryOrderByUpdatedAtDescIdAsc(
            String submitterId,
            SubmissionCategory category,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllByStageOrderByUpdatedAtDescIdAsc(SubmissionStage stage, Pageable pageable);

    @EntityGraph(attributePaths = "responsibleSector")
    Page<Submission> findAllByStageOrderBySubmittedAtDescIdAsc(SubmissionStage stage, Pageable pageable);

    @EntityGraph(attributePaths = "responsibleSector")
    @Query("""
            select submission
            from Submission submission
            where (submission.code = :code or submission.projectCode = :code)
              and """ + SubmissionVisibilityPredicate.QUERY)
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
                  :dateFiltered = false
                  or (
                      submission.receivedAt is not null
                      and submission.receivedAt >= :receivedFrom
                      and submission.receivedAt < :receivedTo
                  )
              )
              and (
                  coalesce(:query, '') = ''
                  or lower(submission.code) like lower(concat('%', coalesce(:query, ''), '%')) escape '\\'
                  or lower(coalesce(submission.projectCode, ''))
                      like lower(concat('%', coalesce(:query, ''), '%')) escape '\\'
                  or lower(submission.title) like lower(concat('%', coalesce(:query, ''), '%')) escape '\\'
                  or lower(coalesce(submission.submitterName, ''))
                      like lower(concat('%', coalesce(:query, ''), '%')) escape '\\'
              )
              and """ + SubmissionVisibilityPredicate.QUERY + """
            order by case when submission.receivedAt is null then 1 else 0 end,
                     submission.receivedAt desc,
                     submission.id asc
            """)
    Page<Submission> searchVisible(
            @Param("category") SubmissionCategory category,
            @Param("stage") SubmissionStage stage,
            @Param("sectorId") UUID sectorId,
            @Param("query") String query,
            @Param("dateFiltered") boolean dateFiltered,
            @Param("receivedFrom") Instant receivedFrom,
            @Param("receivedTo") Instant receivedTo,
            @Param("scope") String scope,
            @Param("principal") String principal,
            Pageable pageable
    );
}
