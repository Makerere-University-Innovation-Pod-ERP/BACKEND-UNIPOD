CREATE TABLE document_blobs (
    id UUID NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_document_blobs PRIMARY KEY (id)
);
