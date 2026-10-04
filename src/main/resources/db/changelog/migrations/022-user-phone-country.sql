--liquibase formatted sql

--changeset liquibase:022-user-phone-country logicalFilePath:migrations/022-user-phone-country.sql
-- The user's own phone country (ISO 3166-1 alpha-2, drives the flag in the profile form),
-- independent of the org's phone country code. users.phone itself stays the full E.164
-- number. NULL = not chosen; the UI infers it from a '+' number when it can.
ALTER TABLE users ADD COLUMN phone_country_iso CHAR(2) NULL;
--rollback ALTER TABLE users DROP COLUMN phone_country_iso;
