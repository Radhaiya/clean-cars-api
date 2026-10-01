--liquibase formatted sql

--changeset liquibase:018-amc-row-quantity logicalFilePath:migrations/018-amc-row-quantity.sql
-- Quantity per AMC service row (like service-order lines): a bundle row is price x quantity. Existing rows are 1.
-- Redemption order lines copy it, and the sale price (Σ rows x quantity x slots) follows from it.
ALTER TABLE amc_variant_rows
  ADD COLUMN quantity INT NOT NULL DEFAULT 1 AFTER plan_item_id;

ALTER TABLE amc_subscription_items
  ADD COLUMN quantity INT NOT NULL DEFAULT 1 AFTER position;

--rollback ALTER TABLE amc_subscription_items DROP COLUMN quantity; ALTER TABLE amc_variant_rows DROP COLUMN quantity;
