--liquibase formatted sql

--changeset liquibase:002-organization-currency
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'organizations' AND column_name = 'currency_code'
-- The org's display currency: ISO 4217 code (e.g. 'USD') plus its symbol (e.g. '$'),
-- both set from the code by the app. Compulsory when an org is created
-- (StartTrialRequest); the DEFAULTs only backfill rows that predate the columns.
ALTER TABLE organizations
  ADD COLUMN currency_code   CHAR(3)     NOT NULL DEFAULT 'INR' AFTER timezone,
  ADD COLUMN currency_symbol VARCHAR(16) NOT NULL DEFAULT '₹'   AFTER currency_code;
--rollback ALTER TABLE organizations DROP COLUMN currency_symbol, DROP COLUMN currency_code;
