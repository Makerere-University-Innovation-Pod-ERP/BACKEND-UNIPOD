package org.unipod.unipoderpbackend.repository.review;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.unipod.unipoderpbackend.model.review.ReviewAssignment;
import org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus;

public interface ReviewAssignmentRepository extends JpaRepository<ReviewAssignment, Long> {
    /**
     * The caller's assignments that belong in their review queue: matched on reviewer ignoring case,
     * only in each brief's latest review round, and in SEQUENTIAL mode only once every assignment
     * with a lower position in that round is SUBMITTED. Joins Submission by id (no association).
     */
    String QUEUE_FROM_WHERE = """
            from ReviewAssignment a
            join Submission s on s.id = a.briefId
            join ReviewSettings rs on rs.briefId = a.briefId and rs.round = a.round
            where lower(a.reviewerId) = lower(:reviewer)
              and a.round = (select max(latest.round) from ReviewSettings latest where latest.briefId = a.briefId)
              and (
                  rs.mode = org.unipod.unipoderpbackend.model.review.ReviewMode.PARALLEL
                  or not exists (
                      select earlier.id from ReviewAssignment earlier
                      where earlier.briefId = a.briefId
                        and earlier.round = a.round
                        and earlier.position < a.position
                        and earlier.status <> org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus.SUBMITTED
                  )
              )
            """;

    List<ReviewAssignment> findByBriefIdAndRoundOrderByPositionAsc(UUID briefId, int round);

    List<ReviewAssignment> findByReviewerIdAndStatus(String reviewerId, ReviewAssignmentStatus status);

    long countByBriefIdAndRoundAndStatus(UUID briefId, int round, ReviewAssignmentStatus status);

    @Query(value = """
            select new org.unipod.unipoderpbackend.repository.review.ReviewQueueEntry(
                a.id, s.id, s.title, s.code, s.category, a.round, a.position, rs.dueDate, a.status, s.stage)
            """ + QUEUE_FROM_WHERE + """
            order by rs.dueDate asc nulls last, a.id desc
            """,
            countQuery = "select count(a) " + QUEUE_FROM_WHERE)
    Page<ReviewQueueEntry> findQueue(@Param("reviewer") String reviewer, Pageable pageable);

    /** How many of the caller's queue rows can be submitted now: NOT_STARTED on a brief in review. */
    @Query("select count(a) " + QUEUE_FROM_WHERE + """
              and a.status = org.unipod.unipoderpbackend.model.review.ReviewAssignmentStatus.NOT_STARTED
              and s.stage = org.unipod.unipoderpbackend.model.submission.SubmissionStage.IN_REVIEW
            """)
    long countQueueWaiting(@Param("reviewer") String reviewer);
}
