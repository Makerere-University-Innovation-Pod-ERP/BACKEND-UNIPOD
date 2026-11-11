ALTER TABLE fact_finding_checks
    ALTER COLUMN title_note TYPE VARCHAR(1000),
    ALTER COLUMN purpose_note TYPE VARCHAR(1000),
    ALTER COLUMN evidence_note TYPE VARCHAR(1000),
    ALTER COLUMN relevant_note TYPE VARCHAR(1000);
