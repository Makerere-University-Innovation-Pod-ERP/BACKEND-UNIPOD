CREATE TABLE report_exports (
    id           UUID         NOT NULL,
    report       VARCHAR(40)  NOT NULL,
    format       VARCHAR(10)  NOT NULL,
    requested_by VARCHAR(254) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    file_name    VARCHAR(120) NOT NULL,
    content      BYTEA        NOT NULL,
    CONSTRAINT pk_report_exports PRIMARY KEY (id),
    CONSTRAINT ck_report_exports_report CHECK (report IN (
        'SUBMISSIONS_BY_STAGE', 'STUDENT_VS_PUBLIC', 'COMPLETION_RATE', 'INCOME_TREND', 'DEPARTMENT_WORKLOAD'
    )),
    CONSTRAINT ck_report_exports_format CHECK (format IN ('CSV'))
);

CREATE INDEX ix_report_exports_requester ON report_exports (requested_by, created_at DESC);
