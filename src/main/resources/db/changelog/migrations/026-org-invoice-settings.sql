--liquibase formatted sql

--changeset liquibase:026-org-invoice-settings logicalFilePath:migrations/026-org-invoice-settings.sql
-- Per-org look of the printed invoice, set by the owner in Profile -> Invoice Settings:
-- one of four templates and the accent colour (#RRGGBB) used for the table header and section
-- bars. NULL = never chosen: the app falls back to the CLASSIC template and its default colour.
ALTER TABLE organizations ADD COLUMN invoice_template VARCHAR(16) NULL;
ALTER TABLE organizations ADD COLUMN invoice_color CHAR(7) NULL;
--rollback ALTER TABLE organizations DROP COLUMN invoice_color;
--rollback ALTER TABLE organizations DROP COLUMN invoice_template;
