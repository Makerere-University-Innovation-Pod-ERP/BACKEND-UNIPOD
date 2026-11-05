ALTER TABLE submissions
    DROP CONSTRAINT ck_student_submission_details;

ALTER TABLE submissions
    ADD CONSTRAINT ck_student_submission_details CHECK (
        stage <> 'RECEIVED' OR category <> 'STUDENT' OR (
            student_number IS NOT NULL AND length(btrim(student_number)) > 0
            AND programme IS NOT NULL AND length(btrim(programme)) > 0
            AND supervisor IS NOT NULL AND length(btrim(supervisor)) > 0
            AND recommendation_letter_status IS NOT NULL
            AND recommendation_letter_status IN ('VERIFIED', 'SUBMITTED', 'ONE_FILE', 'NOT_REQUIRED')
        )
    );
