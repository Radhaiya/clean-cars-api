--liquibase formatted sql

--changeset liquibase:007-org-tax-name logicalFilePath:migrations/007-org-tax-name.sql
-- The org's tax label — one of the curated names from GET /api/reference /taxes
-- (GST (India), VAT, Sales Tax, …) or any custom label; NULL = unset (the UI
-- falls back to a generic "Tax"). Display-only: a naming convention for
-- invoices/reads, and a default prefill for forms. Rates stay per line item
-- (taxPercentage / taxIncluded), unchanged.
ALTER TABLE organizations ADD COLUMN tax_name VARCHAR(64) NULL;
--rollback ALTER TABLE organizations DROP COLUMN tax_name;
