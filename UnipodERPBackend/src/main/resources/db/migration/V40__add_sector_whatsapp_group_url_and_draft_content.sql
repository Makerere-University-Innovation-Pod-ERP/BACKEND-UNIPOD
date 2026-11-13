-- Draft briefs keep their content in progress, so the required-content checks
-- only apply once the brief leaves DRAFT. Departments also expose a WhatsApp
-- group invite link on the confirmation screen.

ALTER TABLE sectors
    ADD COLUMN whatsapp_group_url VARCHAR(500);

ALTER TABLE submissions
    DROP CONSTRAINT ck_submission_title,
    DROP CONSTRAINT ck_submission_purpose,
    DROP CONSTRAINT ck_submission_problem;

ALTER TABLE submissions
    ADD CONSTRAINT ck_submission_title
        CHECK (stage = 'DRAFT' OR length(btrim(title)) > 0),
    ADD CONSTRAINT ck_submission_purpose
        CHECK (stage = 'DRAFT' OR length(btrim(purpose)) > 0),
    ADD CONSTRAINT ck_submission_problem
        CHECK (stage = 'DRAFT' OR length(btrim(problem_addressed)) > 0);
