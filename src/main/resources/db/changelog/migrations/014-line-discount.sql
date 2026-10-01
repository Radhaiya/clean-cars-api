--liquibase formatted sql

--changeset liquibase:014-line-discount logicalFilePath:migrations/014-line-discount.sql
-- Per-line discount on service orders, stored as an AMOUNT per unit (the percent is derived
-- at read time). It comes off the line's base_price before tax; 0 <= discount_amount <= base_price
-- (validated app-side). Existing lines default to 0, so nothing changes for existing orders.
ALTER TABLE service_order_items
  ADD COLUMN discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER quantity;

-- Zero-total orders now count as paid (nothing to receive). Back-fill the derived flag for
-- existing orders that already have lines and only zero-priced ones.
UPDATE service_orders so
SET so.paid = 1
WHERE so.paid = 0
  AND EXISTS (SELECT 1 FROM service_order_items i WHERE i.service_order_id = so.id)
  AND NOT EXISTS (SELECT 1 FROM service_order_items i WHERE i.service_order_id = so.id AND i.base_price > 0);

--rollback ALTER TABLE service_order_items DROP COLUMN discount_amount;
