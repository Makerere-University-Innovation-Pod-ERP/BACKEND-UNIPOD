package org.unipod.unipoderpbackend.repository.improvement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.improvement.RefinementRequest;
import org.unipod.unipoderpbackend.model.improvement.RefinementStatus;

public interface RefinementRequestRepository extends JpaRepository<RefinementRequest, Long> {
    /**
     * Briefs in the staff refinement queue, each with its latest request (highest cycle) or none: every brief in
     * IMPROVEMENT_REQUESTED, and every brief in RESUBMITTED whose latest request is RESPONDED. Joins Submission
     * and RefinementRequest by id (no association); the left join keeps briefs without any request.
     */
    String QUEUE_FROM_WHERE = """
            from Submission s
            left join RefinementRequest r on r.briefId = s.id
                and r.cycle = (select max(r2.cycle) from RefinementRequest r2 where r2.briefId = s.id)
            where s.stage = org.unipod.unipoderpbackend.model.submission.SubmissionStage.IMPROVEMENT_REQUESTED
               or (s.stage = org.unipod.unipoderpbackend.model.submission.SubmissionStage.RESUBMITTED
                   and r.status = org.unipod.unipoderpbackend.model.improvement.RefinementStatus.RESPONDED)
            """;

    Optional<RefinementRequest> findTopByBriefIdOrderByCycleDesc(UUID briefId);

    Optional<RefinementRequest> findTopByBriefIdAndStatusOrderByCycleDesc(
            UUID briefId,
            RefinementStatus status
    );

    List<RefinementRequest> findByBriefIdOrderByCycleAsc(UUID briefId);

    boolean existsByBriefIdAndStatus(UUID briefId, RefinementStatus status);

    long countByBriefId(UUID briefId);

    /**
     * Overdue rows first (IMPROVEMENT_REQUESTED, latest request OPEN, due before {@code today}), then due date
     * ascending with briefs without a request last, then the brief's last update (newest first), then its id.
     */
    @Query(value = """
            select new org.unipod.unipoderpbackend.repository.improvement.RefinementQueueEntry(
                s.id, s.title, s.code, s.category, s.stage, s.updatedAt,
                r.id, r.cycle, r.source, r.dueDate, r.status)
            """ + QUEUE_FROM_WHERE + """
            order by
                case when s.stage = org.unipod.unipoderpbackend.model.submission.SubmissionStage.IMPROVEMENT_REQUESTED
                          and r.status = org.unipod.unipoderpbackend.model.improvement.RefinementStatus.OPEN
                          and r.dueDate < :today
                     then 0 else 1 end,
                r.dueDate asc nulls last,
                s.updatedAt desc,
                s.id asc
            """,
            countQuery = "select count(s) " + QUEUE_FROM_WHERE)
    Page<RefinementQueueEntry> findQueue(@Param("today") LocalDate today, Pageable pageable);
}
