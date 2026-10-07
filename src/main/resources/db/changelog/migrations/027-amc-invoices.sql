--liquibase formatted sql

--changeset liquibase:027-amc-invoices logicalFilePath:migrations/027-amc-invoices.sql
-- Invoices for AMC sales (one per sold AMC, the upfront payment). Numbered AMC-0001, AMC-0002, ...
-- on its own per-org sequence, separate from the service-order invoices (025), handed out under a
-- lock on the org row. Plan, rows and prices are read live from the AMC snapshot; only the issue
-- date and a note are stored here.
CREATE TABLE amc_invoices (
  id                   BINARY(16) PRIMARY KEY,
  org_id               BINARY(16) NOT NULL,
  amc_subscription_id  BINARY(16) NOT NULL,
  invoice_number       INT NOT NULL,
  invoice_date         DATE NOT NULL,
  notes                TEXT NULL,
  created_by           BINARY(16) NOT NULL,
  created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_amc_invoices_subscription (amc_subscription_id),
  UNIQUE KEY uq_amc_invoices_org_number (org_id, invoice_number),
  CONSTRAINT fk_amc_invoices_subscription FOREIGN KEY (amc_subscription_id) REFERENCES amc_subscriptions(id)
);
--rollback DROP TABLE amc_invoices;
