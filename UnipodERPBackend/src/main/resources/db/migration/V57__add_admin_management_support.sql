ALTER TABLE api_users
    ADD COLUMN full_name VARCHAR(200),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE role_permission_configs (
    role VARCHAR(40) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp,
    CONSTRAINT pk_role_permission_configs PRIMARY KEY (role)
);

CREATE TABLE role_permission_grants (
    role VARCHAR(40) NOT NULL,
    permission_id VARCHAR(60) NOT NULL,
    CONSTRAINT pk_role_permission_grants PRIMARY KEY (role, permission_id),
    CONSTRAINT fk_role_permission_grants_config FOREIGN KEY (role)
        REFERENCES role_permission_configs(role) ON DELETE CASCADE
);

INSERT INTO role_permission_configs (role, version)
SELECT role, 0
FROM (
    VALUES
        ('INNOVATION_COORDINATOR'),
        ('DEPARTMENT_COORDINATOR'),
        ('RECEPTIONIST'),
        ('FACT_FINDING_OFFICER'),
        ('REVIEWER'),
        ('SECTOR_OFFICER'),
        ('IP_LIAISON'),
        ('SUBMITTER'),
        ('FINANCE_OFFICER'),
        ('OBSERVER'),
        ('TELECOMS_DOMAIN_OWNER')
) AS roles(role);

INSERT INTO role_permission_grants (role, permission_id)
VALUES
    ('INNOVATION_COORDINATOR', 'CASE_VIEW'),
    ('INNOVATION_COORDINATOR', 'CASE_EDIT'),
    ('INNOVATION_COORDINATOR', 'CASE_ASSIGN_REVIEWER'),
    ('INNOVATION_COORDINATOR', 'CASE_DECIDE'),
    ('INNOVATION_COORDINATOR', 'AUDIT_VIEW'),
    ('INNOVATION_COORDINATOR', 'REPORT_VIEW'),
    ('DEPARTMENT_COORDINATOR', 'CASE_VIEW'),
    ('DEPARTMENT_COORDINATOR', 'PRICE_LIST_PROPOSE'),
    ('RECEPTIONIST', 'CASE_VIEW'),
    ('RECEPTIONIST', 'CASE_EDIT'),
    ('RECEPTIONIST', 'CASE_FORWARD'),
    ('FACT_FINDING_OFFICER', 'CASE_VIEW'),
    ('FACT_FINDING_OFFICER', 'CASE_EDIT'),
    ('REVIEWER', 'CASE_VIEW'),
    ('REVIEWER', 'CASE_REVIEW'),
    ('SECTOR_OFFICER', 'CASE_VIEW'),
    ('SUBMITTER', 'CASE_VIEW'),
    ('SUBMITTER', 'CASE_EDIT'),
    ('OBSERVER', 'CASE_VIEW'),
    ('OBSERVER', 'AUDIT_VIEW'),
    ('OBSERVER', 'REPORT_VIEW'),
    ('FINANCE_OFFICER', 'PRICE_LIST_MANAGE');
