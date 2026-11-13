ALTER TABLE api_users DROP CONSTRAINT ck_api_users_role;

ALTER TABLE api_users ADD CONSTRAINT ck_api_users_role CHECK (role IN (
    'ADMINISTRATOR',
    'INNOVATION_COORDINATOR',
    'RECEPTIONIST',
    'FACT_FINDING_OFFICER',
    'REVIEWER',
    'SECTOR_OFFICER',
    'IP_LIAISON',
    'SUBMITTER',
    'OBSERVER',
    'FINANCE_OFFICER',
    'DEPARTMENT_HEAD'
));

CREATE INDEX ix_api_users_role ON api_users (role);
