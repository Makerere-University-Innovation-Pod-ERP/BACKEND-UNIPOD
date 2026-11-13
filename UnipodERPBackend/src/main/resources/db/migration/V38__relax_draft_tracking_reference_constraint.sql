-- Tracking references are allocated when a draft is created, so drafts may already have a code.

ALTER TABLE submissions
    DROP CONSTRAINT ck_submission_receipt_state;

ALTER TABLE submissions
    ADD CONSTRAINT ck_submission_receipt_state CHECK (
        (stage = 'DRAFT'
            AND received_at IS NULL
            AND receiving_officer IS NULL
            AND responsible_sector_id IS NULL
            AND forwarded_at IS NULL)
        OR
        (stage IN ('SUBMITTED', 'AWAITING_INFORMATION')
            AND code IS NOT NULL
            AND received_at IS NOT NULL
            AND responsible_sector_id IS NULL
            AND forwarded_at IS NULL)
        OR
        (stage IN (
                'FORWARDED', 'IN_FACT_FINDING', 'READY_FOR_REVIEW', 'IN_REVIEW',
                'IMPROVEMENT_REQUESTED', 'RESUBMITTED', 'CONFIRMED_AS_PROJECT',
                'AWAITING_PAYMENT', 'IN_PROGRESS', 'COMPLETED', 'CLOSED'
            )
            AND code IS NOT NULL
            AND received_at IS NOT NULL)
    );
