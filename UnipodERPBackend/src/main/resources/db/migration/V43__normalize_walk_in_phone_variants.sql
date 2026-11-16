-- V42 only folded national mobile numbers (07XXXXXXXX). The same Ugandan
-- number is also typed with an international 00 prefix, with a trunk zero after
-- the country code ("+256 (0)700...") and as a landline, so extend the
-- normaliser and rebuild the expression index that depends on it.

DROP INDEX IF EXISTS ix_user_profiles_phone_normalized;

CREATE OR REPLACE FUNCTION normalize_phone_number(value text)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
    WITH digits AS (
        SELECT regexp_replace(COALESCE(value, ''), '[^0-9]', '', 'g') AS raw
    ),
    without_international_prefix AS (
        SELECT CASE
            WHEN raw LIKE '00%' THEN substr(raw, 3)
            ELSE raw
        END AS value
        FROM digits
    ),
    without_trunk_zero AS (
        SELECT CASE
            WHEN value LIKE '2560%' THEN '256' || substr(value, 5)
            ELSE value
        END AS value
        FROM without_international_prefix
    )
    SELECT CASE
        WHEN value ~ '^0[0-9]{8,9}$' THEN '256' || substr(value, 2)
        ELSE value
    END
    FROM without_trunk_zero
$$;

CREATE INDEX ix_user_profiles_phone_normalized
    ON user_profiles (normalize_phone_number(phone));
