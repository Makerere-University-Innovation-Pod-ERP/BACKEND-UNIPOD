-- Enforce case-insensitive facility names.

-- Rename existing case-variant duplicates so the unique index can be created. The
-- id suffix guarantees the renamed value is unique and does not collide with a
-- facility that already carries a similar suffix.
WITH ranked AS (
    SELECT id,
           row_number() OVER (PARTITION BY lower(name) ORDER BY created_at, id) AS duplicate_rank
    FROM facilities
)
UPDATE facilities facility
SET name = left(facility.name, 140) || ' [' || substr(ranked.id::text, 1, 8) || ']'
FROM ranked
WHERE facility.id = ranked.id
  AND ranked.duplicate_rank > 1;

CREATE UNIQUE INDEX uq_facilities_name_lower ON facilities (lower(name));
