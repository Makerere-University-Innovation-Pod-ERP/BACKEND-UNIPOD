package org.unipod.unipoderpbackend.repository.improvement;

/**
 * Counters over every row of the staff refinement queue, as read by
 * {@link RefinementRequestRepository#countQueue}. They classify rows exactly as the queue rows do:
 * {@code waitingForSubmitter} is IMPROVEMENT_REQUESTED with an OPEN latest request, {@code overdue} those of them due
 * before today, {@code readyToAccept} is RESUBMITTED (with a RESPONDED latest request), and {@code needsRequest}
 * is IMPROVEMENT_REQUESTED without an OPEN latest request.
 */
public record RefinementQueueCounters(
        long waitingForSubmitter,
        long overdue,
        long readyToAccept,
        long needsRequest
) {
}
