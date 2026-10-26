-- Source of ids for generated short codes (see ShortCodeGenerator).
-- Each nextval() hands an app instance a block of 100 ids: [value, value + 100).
CREATE SEQUENCE short_code_seq START WITH 1 INCREMENT BY 100;

-- The UNIQUE constraint on short_code already creates an index; this one was a duplicate.
DROP INDEX IF EXISTS idx_short_code;
