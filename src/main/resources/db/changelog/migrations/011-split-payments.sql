--liquibase formatted sql

--changeset liquibase:011-split-payments logicalFilePath:migrations/011-split-payments.sql
-- Split payments. `payments` (created in 001, never used) becomes the single ledger
-- of money received per service order — a one-time payment is just one full-amount
-- row, a split order has n rows. service_orders.paid is now derived (amount_paid >=
-- the order's gross total) and stays as a denormalized, indexed-friendly flag;
-- amount_paid is the denormalized sum of the order's payments. payment_type /
-- payment_date move off the order onto each payment.
ALTER TABLE payments
  ADD COLUMN org_id BINARY(16) NULL AFTER id,
  ADD COLUMN payment_date DATE NULL,
  MODIFY COLUMN mode ENUM('card','cash','upi') NULL, -- NULL only for migrated legacy orders that never recorded a method
  DROP COLUMN paid_at,
  DROP COLUMN reference_number;

ALTER TABLE service_orders
  ADD COLUMN payment_plan ENUM('one_time','split') NOT NULL DEFAULT 'one_time' AFTER status,
  ADD COLUMN amount_paid DECIMAL(12,2) NOT NULL DEFAULT 0 AFTER payment_plan;

-- Legacy paid orders -> one full-amount payment each. Line gross mirrors TaxBreakdown:
-- tax-included lines are base*qty, tax-excluded are (round(base)+round(base*rate/100))*qty.
INSERT INTO payments (id, org_id, service_order_id, amount, mode, payment_date, received_by, created_at)
SELECT UNHEX(REPLACE(UUID(), '-', '')), so.org_id, so.id, g.gross, so.payment_type,
       COALESCE(so.payment_date, DATE(so.updated_at)), so.created_by, so.updated_at
FROM service_orders so
JOIN (SELECT service_order_id,
             SUM(CASE WHEN COALESCE(gst_percentage, 0) = 0 OR gst_included = 1
                      THEN ROUND(base_price, 2) * quantity
                      ELSE (ROUND(base_price, 2) + ROUND(base_price * gst_percentage / 100, 2)) * quantity
                 END) AS gross
      FROM service_order_items GROUP BY service_order_id) g ON g.service_order_id = so.id
WHERE so.paid = 1 AND g.gross > 0;

UPDATE service_orders so
JOIN (SELECT service_order_id, SUM(amount) AS amt FROM payments GROUP BY service_order_id) p
  ON p.service_order_id = so.id
SET so.amount_paid = p.amt;

-- A "paid" order with a zero total has nothing received, so it is not paid under the new rule.
UPDATE service_orders SET paid = 0 WHERE amount_paid = 0;

UPDATE payments p JOIN service_orders so ON so.id = p.service_order_id SET p.org_id = so.org_id WHERE p.org_id IS NULL;
ALTER TABLE payments
  MODIFY COLUMN org_id BINARY(16) NOT NULL,
  MODIFY COLUMN payment_date DATE NOT NULL,
  ADD KEY idx_payments_org_id (org_id),
  ADD CONSTRAINT fk_payments_org FOREIGN KEY (org_id) REFERENCES organizations(id);

ALTER TABLE service_orders
  DROP COLUMN payment_type,
  DROP COLUMN payment_date;
--rollback ALTER TABLE service_orders ADD COLUMN payment_date DATE NULL, ADD COLUMN payment_type ENUM('card','cash','upi') NULL;
--rollback UPDATE service_orders so JOIN (SELECT service_order_id, MAX(payment_date) d, SUBSTRING_INDEX(GROUP_CONCAT(mode ORDER BY payment_date DESC), ',', 1) m FROM payments GROUP BY service_order_id) p ON p.service_order_id = so.id SET so.payment_date = p.d, so.payment_type = p.m;
--rollback ALTER TABLE payments DROP FOREIGN KEY fk_payments_org;
--rollback ALTER TABLE payments DROP KEY idx_payments_org_id, DROP COLUMN org_id, DROP COLUMN payment_date, ADD COLUMN paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, ADD COLUMN reference_number VARCHAR(255) NULL, MODIFY COLUMN mode ENUM('cash','card','upi','netbanking','cheque','wallet','credit') NOT NULL;
--rollback ALTER TABLE service_orders DROP COLUMN amount_paid, DROP COLUMN payment_plan;
