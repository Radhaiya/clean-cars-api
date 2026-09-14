-- ============================================================================
-- Bulk demo-data seed script — NOT auto-loaded (lives outside db/schema.sql /
-- db/seed.sql, which are the only files docker-compose mounts into
-- /docker-entrypoint-initdb.d). Run it manually, on demand, against one org.
--
-- Generates, for @target_org_id:
--   - 30 service_catalog rows  ("Seed Service 01".."Seed Service 30"),
--     only if that org doesn't already have them
--   - 300 customers
--   - 10 cars per customer                     = 3,000 cars
--   - 2 service_orders per car                 = 6,000 service_orders
--   - 3-4 service_order_items per order (random, drawn from the 30 services
--     above), quantity 1                       = ~21,000 service_order_items
--
-- All generated created_at / updated_at / completed_at / payment_date values
-- are randomized independently within calendar year 2025 (2025-01-01 to
-- 2025-12-31) — there is deliberately no attempt to keep e.g. a car's
-- created_at after its owning customer's, or an order's after its car's.
-- If you want that chronological consistency instead, say so and this can be
-- reworked to derive each child's date from (parent date + random offset).
--
-- status: 80% completed / 10% cancelled / 10% in_progress, independent of paid.
-- paid:   70% paid / 30% unpaid, independent of status.
-- brand_id / model_id / vendor_id: left NULL (both nullable on `cars` /
-- `service_orders`) — not seeded, per your answer.
-- employee_id: randomly assigned from the org's existing `employees` rows,
-- or left NULL if the org has none.
-- created_by: randomly assigned from the org's existing `users` rows — the
-- org MUST have at least one user, or the script aborts (service_orders.
-- created_by is NOT NULL with no sensible synthetic fallback).
--
-- Usage:
--   1. Edit @target_org_id below.
--   2. docker exec -i cleancars-mysql mysql -uroot -proot cleancars \
--        < src/main/resources/db/scripts/seed-bulk-demo-data.sql
--
-- Re-running for the SAME org: the 30 service_catalog rows are skipped if
-- already present (safe to re-run), but customers/cars/orders/items are NOT
-- de-duplicated — re-running adds another 300 customers etc. on top. Cheap
-- cleanup: customers are named 'Seed Customer N' and service_catalog rows
-- 'Seed Service NN', so a targeted DELETE by those name patterns (cars/
-- orders/items cascade-free, so delete children before parents — or just ask
-- for a cleanup script) removes exactly what this script added.
-- ============================================================================

SET @target_org_id       = 1;      -- <-- CHANGE ME
SET @num_customers       = 300;
SET @cars_per_customer   = 10;
SET @orders_per_car      = 2;
SET @num_catalog_services = 30;

DELIMITER //

DROP PROCEDURE IF EXISTS seed_bulk_demo_data //

CREATE PROCEDURE seed_bulk_demo_data(
    IN p_org_id INT,
    IN p_num_customers INT,
    IN p_cars_per_customer INT,
    IN p_orders_per_car INT,
    IN p_num_catalog_services INT
)
proc_body: BEGIN

    DECLARE v_customer_idx INT DEFAULT 1;
    DECLARE v_car_idx INT;
    DECLARE v_order_idx INT;
    DECLARE v_item_idx INT;
    DECLARE v_items_this_order INT;

    DECLARE v_customer_id BIGINT;
    DECLARE v_car_id BIGINT;
    DECLARE v_order_id BIGINT;

    DECLARE v_created_at DATETIME;
    DECLARE v_status VARCHAR(20);
    DECLARE v_paid BOOLEAN;
    DECLARE v_payment_type VARCHAR(20);
    DECLARE v_completed_at DATETIME;
    DECLARE v_payment_date DATE;

    DECLARE v_user_id BIGINT;
    DECLARE v_user_count INT;
    DECLARE v_employee_id BIGINT;
    DECLARE v_employee_count INT;

    DECLARE v_service_id BIGINT;
    DECLARE v_service_name VARCHAR(255);
    DECLARE v_service_price DECIMAL(12,2);
    DECLARE v_service_gst DECIMAL(5,2);
    DECLARE v_service_gst_incl BOOLEAN;

    DECLARE v_r DOUBLE;
    DECLARE v_pick INT;

    -- ---- Pre-flight: this org must have at least one user for created_by ----
    SELECT COUNT(*) INTO v_user_count FROM users WHERE org_id = p_org_id;
    IF v_user_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'seed_bulk_demo_data: target org has no users — service_orders.created_by cannot be set';
    END IF;

    CREATE TEMPORARY TABLE tmp_org_users AS
        SELECT ROW_NUMBER() OVER (ORDER BY id) AS rn, id
        FROM users WHERE org_id = p_org_id;

    SELECT COUNT(*) INTO v_employee_count FROM employees WHERE org_id = p_org_id;
    IF v_employee_count > 0 THEN
        CREATE TEMPORARY TABLE tmp_org_employees AS
            SELECT ROW_NUMBER() OVER (ORDER BY id) AS rn, id
            FROM employees WHERE org_id = p_org_id;
    END IF;

    -- ---- Seed the 30 catalog services for this org, if not already present ----
    INSERT INTO service_catalog (org_id, name, default_price, gst_percentage, gst_included, created_at)
    SELECT p_org_id,
           CONCAT('Seed Service ', LPAD(seq.n, 2, '0')),
           ROUND(200 + RAND() * 4800, 2),
           ELT(1 + FLOOR(RAND() * 4), 0, 5, 12, 18),
           RAND() < 0.5,
           NOW()
    FROM (
        SELECT tens.n * 10 + ones.n + 1 AS n
        FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
              UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) ones
        CROSS JOIN (SELECT 0 n UNION SELECT 1 UNION SELECT 2) tens
    ) seq
    WHERE seq.n BETWEEN 1 AND p_num_catalog_services
      AND NOT EXISTS (
          SELECT 1 FROM service_catalog sc
          WHERE sc.org_id = p_org_id AND sc.name = CONCAT('Seed Service ', LPAD(seq.n, 2, '0'))
      );

    CREATE TEMPORARY TABLE tmp_org_services AS
        SELECT ROW_NUMBER() OVER (ORDER BY id) AS rn, id, name, default_price, gst_percentage, gst_included
        FROM service_catalog
        WHERE org_id = p_org_id AND name LIKE 'Seed Service %'
        ORDER BY id
        LIMIT 30;

    -- =========================== CUSTOMERS ===========================
    WHILE v_customer_idx <= p_num_customers DO

        SET v_created_at = TIMESTAMP('2025-01-01 00:00:00') + INTERVAL FLOOR(RAND() * 31536000) SECOND;

        INSERT INTO customers (org_id, name, phone, email, address, created_at, updated_at)
        VALUES (
            p_org_id,
            CONCAT(
                ELT(1 + FLOOR(RAND() * 10), 'Ravi','Suresh','Anita','Priya','Vikram','Deepa','Arjun','Kavya','Manoj','Sneha'),
                ' ',
                ELT(1 + FLOOR(RAND() * 10), 'Sharma','Patel','Reddy','Kulkarni','Nair','Iyer','Singh','Rao','Joshi','Mehta')
            ),
            CONCAT('9', LPAD(p_org_id, 3, '0'), LPAD(v_customer_idx, 6, '0')),
            NULL,
            NULL,
            v_created_at,
            v_created_at
        );
        SET v_customer_id = LAST_INSERT_ID();

        -- =========================== CARS (per customer) ===========================
        SET v_car_idx = 1;
        WHILE v_car_idx <= p_cars_per_customer DO

            SET v_created_at = TIMESTAMP('2025-01-01 00:00:00') + INTERVAL FLOOR(RAND() * 31536000) SECOND;

            INSERT INTO cars (org_id, customer_id, car_number, year, color, fuel_type, created_at, updated_at)
            VALUES (
                p_org_id,
                v_customer_id,
                CONCAT(
                    ELT(1 + FLOOR(RAND() * 6), 'MH','KA','TN','DL','GJ','UP'),
                    LPAD(1 + FLOOR(RAND() * 99), 2, '0'),
                    CHAR(65 + FLOOR(RAND() * 26)), CHAR(65 + FLOOR(RAND() * 26)),
                    LPAD(FLOOR(RAND() * 9999), 4, '0')
                ),
                2005 + FLOOR(RAND() * 20),
                ELT(1 + FLOOR(RAND() * 8), 'White','Black','Silver','Red','Blue','Grey','Brown','Green'),
                ELT(1 + FLOOR(RAND() * 6), 'petrol','diesel','electric','hybrid','cng','lpg'),
                v_created_at,
                v_created_at
            );
            SET v_car_id = LAST_INSERT_ID();

            -- =========================== SERVICE ORDERS (per car) ===========================
            SET v_order_idx = 1;
            WHILE v_order_idx <= p_orders_per_car DO

                SET v_created_at = TIMESTAMP('2025-01-01 00:00:00') + INTERVAL FLOOR(RAND() * 31536000) SECOND;

                SET v_r = RAND();
                SET v_status = CASE
                    WHEN v_r < 0.10 THEN 'in_progress'
                    WHEN v_r < 0.20 THEN 'cancelled'
                    ELSE 'completed'
                END;
                SET v_completed_at = CASE WHEN v_status = 'completed'
                    THEN v_created_at + INTERVAL (1 + FLOOR(RAND() * 72)) HOUR ELSE NULL END;

                SET v_paid = RAND() < 0.7;
                SET v_payment_type = CASE WHEN v_paid
                    THEN ELT(1 + FLOOR(RAND() * 3), 'card', 'cash', 'upi') ELSE NULL END;
                SET v_payment_date = CASE WHEN v_paid THEN DATE(v_created_at) ELSE NULL END;

                SET v_pick = 1 + FLOOR(RAND() * v_user_count);
                SELECT id INTO v_user_id FROM tmp_org_users WHERE rn = v_pick;
                IF v_employee_count > 0 THEN
                    SET v_pick = 1 + FLOOR(RAND() * v_employee_count);
                    SELECT id INTO v_employee_id FROM tmp_org_employees WHERE rn = v_pick;
                ELSE
                    SET v_employee_id = NULL;
                END IF;

                INSERT INTO service_orders (
                    org_id, car_id, customer_id, created_by, employee_id, odometer_reading,
                    status, paid, payment_date, payment_type,
                    created_at, updated_at, completed_at
                ) VALUES (
                    p_org_id, v_car_id, v_customer_id, v_user_id, v_employee_id,
                    5000 + FLOOR(RAND() * 145000),
                    v_status, v_paid, v_payment_date, v_payment_type,
                    v_created_at, v_created_at, v_completed_at
                );
                SET v_order_id = LAST_INSERT_ID();

                -- =========================== ITEMS (3-4 per order) ===========================
                SET v_items_this_order = 3 + FLOOR(RAND() * 2);
                SET v_item_idx = 1;
                WHILE v_item_idx <= v_items_this_order DO

                    SET v_pick = 1 + FLOOR(RAND() * p_num_catalog_services);
                    SELECT name, default_price, gst_percentage, gst_included
                        INTO v_service_name, v_service_price, v_service_gst, v_service_gst_incl
                        FROM tmp_org_services WHERE rn = v_pick;

                    INSERT INTO service_order_items (
                        service_order_id, service_name, base_price, gst_percentage, gst_included, quantity, created_at
                    ) VALUES (
                        v_order_id, v_service_name, v_service_price, v_service_gst, v_service_gst_incl, 1, v_created_at
                    );

                    SET v_item_idx = v_item_idx + 1;
                END WHILE;

                SET v_order_idx = v_order_idx + 1;
            END WHILE;

            SET v_car_idx = v_car_idx + 1;
        END WHILE;

        SET v_customer_idx = v_customer_idx + 1;
    END WHILE;

    DROP TEMPORARY TABLE IF EXISTS tmp_org_users;
    DROP TEMPORARY TABLE IF EXISTS tmp_org_employees;
    DROP TEMPORARY TABLE IF EXISTS tmp_org_services;

END proc_body //

DELIMITER ;

-- Wrapping in one transaction makes ~30k inserts dramatically faster than
-- autocommit-per-statement, and means a failure partway through rolls back
-- cleanly instead of leaving a half-seeded org.
SET autocommit = 0;
START TRANSACTION;

CALL seed_bulk_demo_data(@target_org_id, @num_customers, @cars_per_customer, @orders_per_car, @num_catalog_services);

COMMIT;
SET autocommit = 1;

DROP PROCEDURE IF EXISTS seed_bulk_demo_data;

SELECT 'Done' AS status,
       (SELECT COUNT(*) FROM customers WHERE org_id = @target_org_id) AS customers_now,
       (SELECT COUNT(*) FROM cars WHERE org_id = @target_org_id) AS cars_now,
       (SELECT COUNT(*) FROM service_orders WHERE org_id = @target_org_id) AS service_orders_now,
       (SELECT COUNT(*) FROM service_order_items i JOIN service_orders so ON so.id = i.service_order_id WHERE so.org_id = @target_org_id) AS service_order_items_now;
