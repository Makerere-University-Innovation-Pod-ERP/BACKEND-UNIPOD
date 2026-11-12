CREATE TABLE app_settings (
    setting_key VARCHAR(100) NOT NULL,
    value       JSONB        NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    updated_by  VARCHAR(254) NOT NULL,
    CONSTRAINT pk_app_settings PRIMARY KEY (setting_key)
);

INSERT INTO app_settings (setting_key, value, updated_at, updated_by) VALUES
    ('case.code.student_prefix', '"STU"', now(), 'system'),
    ('case.code.general_prefix', '"GEN"', now(), 'system'),
    ('case.code.sequence_digits', '6', now(), 'system'),
    ('review.default_mode', '"PARALLEL"', now(), 'system'),
    ('review.default_required_reviews', '2', now(), 'system'),
    ('documents.recommendation_letter_required_for', '["STUDENT"]', now(), 'system');
