-- The submitter home counts upcoming training sessions by contact email.
-- Match the query's expression and predicate so the lookup can use an index,
-- and so a cancelled registration does not add index entries.
CREATE INDEX ix_training_participants_contact_email
    ON training_participants (lower(contact_email))
    WHERE status <> 'CANCELLED';
