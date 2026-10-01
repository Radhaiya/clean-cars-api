--liquibase formatted sql

--changeset liquibase:010-soft-delete-customers-vehicles logicalFilePath:migrations/010-soft-delete-customers-vehicles.sql
-- Soft delete for customers, cars and bikes. A deleted row stays so its service
-- orders keep resolving. Deleting a customer also wipes its contact PII (phone,
-- alt_phone, email, address, notes) and keeps only the name — so customers.phone
-- becomes nullable, and the (org_id, phone) unique key (NULLs don't collide in
-- MySQL) frees the number for a new customer.
ALTER TABLE customers MODIFY COLUMN phone VARCHAR(255) NULL;
ALTER TABLE customers ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cars ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bikes ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
--rollback ALTER TABLE bikes DROP COLUMN is_deleted;
--rollback ALTER TABLE cars DROP COLUMN is_deleted;
--rollback ALTER TABLE customers DROP COLUMN is_deleted;
--rollback ALTER TABLE customers MODIFY COLUMN phone VARCHAR(255) NOT NULL;
