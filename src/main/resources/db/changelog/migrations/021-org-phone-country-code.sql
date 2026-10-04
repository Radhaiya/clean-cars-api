--liquibase formatted sql

--changeset liquibase:021-org-phone-country-code logicalFilePath:migrations/021-org-phone-country-code.sql
-- The org's phone country code — the one source of truth for the dial-code prefix shown
-- on every phone number (customers, vendors, org contact, ...). phone_country_iso is the
-- ISO 3166-1 alpha-2 country (drives the flag; +1 is shared by many countries) and
-- phone_dial_code is its calling code ("+91"), resolved server-side from the ISO code.
-- Both NULL = not chosen yet: existing orgs keep working untouched and are nudged from
-- the Profile page; adding a customer requires it. Stored phone numbers are NOT rewritten
-- — the prefix is applied at display time only.
ALTER TABLE organizations ADD COLUMN phone_country_iso CHAR(2) NULL;
ALTER TABLE organizations ADD COLUMN phone_dial_code VARCHAR(8) NULL;
--rollback ALTER TABLE organizations DROP COLUMN phone_dial_code;
--rollback ALTER TABLE organizations DROP COLUMN phone_country_iso;
