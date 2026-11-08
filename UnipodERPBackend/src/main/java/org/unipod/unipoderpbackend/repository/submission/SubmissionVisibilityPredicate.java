package org.unipod.unipoderpbackend.repository.submission;

final class SubmissionVisibilityPredicate {
    static final String QUERY = """
            (
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
            """;

    private SubmissionVisibilityPredicate() {
    }
}
