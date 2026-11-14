ALTER TABLE report_exports DROP CONSTRAINT ck_report_exports_format;

ALTER TABLE report_exports ADD CONSTRAINT ck_report_exports_format CHECK (format IN ('CSV', 'PDF'));

ALTER TABLE report_exports ADD COLUMN content_type VARCHAR(100) NOT NULL DEFAULT 'text/csv';
