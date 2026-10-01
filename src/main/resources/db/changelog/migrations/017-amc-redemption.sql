--liquibase formatted sql

--changeset liquibase:017-amc-redemption logicalFilePath:migrations/017-amc-redemption.sql
-- An AMC redemption is a normal service order tagged with the AMC it used and the slot (period index,
-- 0-based) it took. A cancelled order frees its slot (counts ignore cancelled orders and a cancelled AMC
-- order can never be reopened), so "used" is derived from these columns — nothing else is stored.
ALTER TABLE service_orders
  ADD COLUMN amc_subscription_id BINARY(16) NULL,
  ADD COLUMN amc_slot_index INT NULL,
  ADD CONSTRAINT fk_service_orders_amc FOREIGN KEY (amc_subscription_id) REFERENCES amc_subscriptions (id),
  ADD KEY idx_service_orders_amc (amc_subscription_id, amc_slot_index);

--rollback ALTER TABLE service_orders DROP FOREIGN KEY fk_service_orders_amc, DROP KEY idx_service_orders_amc, DROP COLUMN amc_subscription_id, DROP COLUMN amc_slot_index;
