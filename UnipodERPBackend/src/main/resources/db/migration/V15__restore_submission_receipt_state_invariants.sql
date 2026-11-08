ALTER TABLE submissions
    DROP CONSTRAINT ck_submission_receipt_state;

ALTER TABLE submissions
    ADD CONSTRAINT ck_submission_receipt_state CHECK (
        (stage = 'DRAFT'
            AND code IS NULL AND received_at IS NULL AND receiving_officer IS NULL
            AND responsible_sector_id IS NULL AND forwarded_at IS NULL)
        OR
        (stage = 'RECEIVED'
            AND code IS NOT NULL AND received_at IS NOT NULL AND receiving_officer IS NOT NULL
            AND responsible_sector_id IS NULL AND forwarded_at IS NULL)
        OR
        (stage IN ('FORWARDED', 'IN_PROGRESS')
            AND code IS NOT NULL AND received_at IS NOT NULL AND receiving_officer IS NOT NULL
            AND responsible_sector_id IS NOT NULL AND forwarded_at IS NOT NULL)
        OR
        (stage IN (
                'REGISTERED', 'FACT_FINDING', 'UNDER_REVIEW',
                'REFINEMENT_REQUESTED', 'FURTHER_HANDLING', 'COMPLETED'
            )
            AND code IS NOT NULL AND received_at IS NOT NULL AND receiving_officer IS NOT NULL
            AND (
                (responsible_sector_id IS NULL AND forwarded_at IS NULL)
                OR
                (responsible_sector_id IS NOT NULL AND forwarded_at IS NOT NULL)
            ))
    );
