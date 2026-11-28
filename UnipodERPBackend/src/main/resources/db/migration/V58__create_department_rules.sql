CREATE TABLE department_rules (
    id UUID NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    scope VARCHAR(10) NOT NULL,
    scope_id UUID,
    must_accept BOOLEAN NOT NULL,
    rule_version INTEGER NOT NULL DEFAULT 1,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_department_rules PRIMARY KEY (id),
    CONSTRAINT fk_department_rules_sector FOREIGN KEY (scope_id) REFERENCES sectors (id),
    CONSTRAINT ck_department_rules_title CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_department_rules_body CHECK (length(btrim(body)) > 0),
    CONSTRAINT ck_department_rules_scope CHECK (
        (scope = 'GENERAL' AND scope_id IS NULL)
        OR (scope = 'SECTOR' AND scope_id IS NOT NULL)
    ),
    CONSTRAINT ck_department_rules_scope_value CHECK (scope IN ('GENERAL', 'SECTOR')),
    CONSTRAINT ck_department_rules_version CHECK (rule_version >= 1)
);

CREATE INDEX ix_department_rules_visibility_updated
    ON department_rules (published, updated_at DESC, title);

CREATE INDEX ix_department_rules_sector
    ON department_rules (scope_id, updated_at DESC)
    WHERE scope = 'SECTOR';

INSERT INTO department_rules (
    id, title, body, scope, scope_id, must_accept, rule_version,
    published, row_version, created_at, updated_at
) VALUES
    (
        '11111111-1111-4111-8111-111111111111',
        'Originality and attribution',
        'Work must be original or properly attributed. Submissions must not infringe existing intellectual property.',
        'GENERAL', NULL, TRUE, 1, TRUE, 0,
        TIMESTAMPTZ '2026-09-01T00:00:00Z', TIMESTAMPTZ '2026-09-01T00:00:00Z'
    ),
    (
        '22222222-2222-4222-8222-222222222222',
        'Good-faith participation',
        'All participants must engage in good faith with the review process.',
        'GENERAL', NULL, TRUE, 1, TRUE, 0,
        TIMESTAMPTZ '2026-09-01T00:00:00Z', TIMESTAMPTZ '2026-09-01T00:00:00Z'
    ),
    (
        '33333333-3333-4333-8333-333333333333',
        'Impartial review',
        'Reviews must be impartial and evidence-based; conflicts of interest must be declared before a decision is recorded.',
        'GENERAL', NULL, FALSE, 1, TRUE, 0,
        TIMESTAMPTZ '2026-09-01T00:00:00Z', TIMESTAMPTZ '2026-09-01T00:00:00Z'
    );
