-- The officer is recorded as the JWT subject, which is the user's email.
-- api_users.email allows 254 characters, so officer_id must match it.
ALTER TABLE fact_finding_checks
    ALTER COLUMN officer_id TYPE VARCHAR(254);
