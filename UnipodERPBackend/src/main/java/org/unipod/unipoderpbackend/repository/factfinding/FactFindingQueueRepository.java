package org.unipod.unipoderpbackend.repository.factfinding;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.unipod.unipoderpbackend.model.factfinding.FactFindingCheck;

/**
 * The fact-finding officer's queue of briefs waiting for the first check (screen F1). A separate repository so no
 * existing repository file changes.
 */
public interface FactFindingQueueRepository extends JpaRepository<FactFindingCheck, Long> {
    /**
     * Briefs forwarded or in fact-finding, except service requests (which skip fact-finding), each with its round-1
     * check if one exists. Joins Submission and FactFindingCheck by id (no association); the left join keeps briefs
     * without a check. Round 1 is the only round today. Completed checks are not filtered out: in normal flows a
     * completed check moves the brief out of these stages, and any such row is reported as it is.
     */
    String QUEUE_FROM_WHERE = """
            from Submission s
            left join FactFindingCheck c on c.briefId = s.id and c.round = 1
            where s.stage in (org.unipod.unipoderpbackend.model.submission.SubmissionStage.FORWARDED,
                              org.unipod.unipoderpbackend.model.submission.SubmissionStage.IN_FACT_FINDING)
              and s.category <> org.unipod.unipoderpbackend.model.submission.SubmissionCategory.SERVICE_REQUEST
            """;

    /** Oldest forwarded first (briefs without a forwarded time last), then the brief id. */
    @Query(value = """
            select new org.unipod.unipoderpbackend.repository.factfinding.FactFindingQueueEntry(
                s.id, s.title, s.code, s.category, s.stage, s.forwardedAt, s.updatedAt,
                c.id, c.officerId, c.updatedAt)
            """ + QUEUE_FROM_WHERE + """
            order by s.forwardedAt asc nulls last, s.id asc
            """,
            countQuery = "select count(s) " + QUEUE_FROM_WHERE)
    Page<FactFindingQueueEntry> findQueue(Pageable pageable);
}
