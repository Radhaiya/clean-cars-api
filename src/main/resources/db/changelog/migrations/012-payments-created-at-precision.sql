--liquibase formatted sql

--changeset liquibase:012-payments-created-at-precision logicalFilePath:migrations/012-payments-created-at-precision.sql
-- Sub-second created_at so splits recorded in the same second keep a stable order
-- (payments are listed by payment_date, then created_at).
ALTER TABLE payments MODIFY COLUMN created_at TIMESTAMP(6) NULL DEFAULT CURRENT_TIMESTAMP(6);
--rollback ALTER TABLE payments MODIFY COLUMN created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP;
