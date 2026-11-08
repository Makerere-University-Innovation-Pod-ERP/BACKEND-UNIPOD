CREATE TABLE api_users (
    id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_api_users PRIMARY KEY (id),
    CONSTRAINT ck_api_users_role CHECK (role IN (
        'ADMINISTRATOR',
        'INNOVATION_COORDINATOR',
        'RECEPTIONIST',
        'FACT_FINDING_OFFICER',
        'REVIEWER',
        'SECTOR_OFFICER',
        'IP_LIAISON',
        'SUBMITTER',
        'OBSERVER'
    ))
);

CREATE UNIQUE INDEX uq_api_users_email ON api_users (lower(email));
