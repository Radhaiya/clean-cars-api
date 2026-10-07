--liquibase formatted sql

--changeset liquibase:025-invoices logicalFilePath:migrations/025-invoices.sql
-- Invoices: at most one per service order (the printable A4 tax invoice). The invoice
-- number is a per-org running sequence handed out under a lock on the org row. The
-- lines, totals and payments are NOT copied here — they are read live from the order;
-- only what the order does not hold (issue date, next-service hints, notes) is stored.
CREATE TABLE invoices (
  id                BINARY(16) PRIMARY KEY,
  org_id            BINARY(16) NOT NULL,
  service_order_id  BINARY(16) NOT NULL,
  invoice_number    INT NOT NULL,
  invoice_date      DATE NOT NULL,
  next_service_date DATE NULL,
  next_service_km   INT NULL,
  notes             TEXT NULL,
  created_by        BINARY(16) NOT NULL,
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_invoices_order (service_order_id),
  UNIQUE KEY uq_invoices_org_number (org_id, invoice_number),
  CONSTRAINT fk_invoices_order FOREIGN KEY (service_order_id) REFERENCES service_orders(id)
);
--rollback DROP TABLE invoices;
