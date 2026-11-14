-- Walk-in intake compared phone numbers as raw strings, so the same number
-- typed as "+256 700 000000", "0700-000000" or "256700000000" did not match
-- the stored profile. Normalise both sides of the lookup instead of rewriting
-- stored values, so existing rows keep matching without a data migration.

CREATE OR REPLACE FUNCTION normalize_phone_number(value text)
RETURNS text
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT CASE
        WHEN regexp_replace(COALESCE(value, ''), '[^0-9]', '', 'g') ~ '^0?7[0-9]{8}$'
            THEN '256' || regexp_replace(
                    regexp_replace(COALESCE(value, ''), '[^0-9]', '', 'g'),
                    '^0',
                    ''
                 )
        ELSE regexp_replace(COALESCE(value, ''), '[^0-9]', '', 'g')
    END
$$;

CREATE INDEX ix_user_profiles_phone_normalized
    ON user_profiles (normalize_phone_number(phone));
