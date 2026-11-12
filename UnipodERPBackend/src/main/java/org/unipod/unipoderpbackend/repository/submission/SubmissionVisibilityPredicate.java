package org.unipod.unipoderpbackend.repository.submission;

/**
 * Row-level visibility for submission reads.
 *
 * <p>Drafts are private to the person who created them ({@code registeredBy})
 * and the submitter they belong to ({@code submitterId}). Every other scope only
 * sees a brief once it has left {@code DRAFT}.</p>
 */
final class SubmissionVisibilityPredicate {
    static final String QUERY = """
            (
                (submission.stage <> 'DRAFT'
                    or submission.registeredBy = :principal
                    or submission.submitterId = :principal)
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
            )
            """;

    private SubmissionVisibilityPredicate() {
    }
}
