--liquibase formatted sql

--changeset liquibase:028-org-distance-unit logicalFilePath:migrations/028-org-distance-unit.sql
-- Per-org distance unit for odometer readings: KM (default) or MI. Display-only, like the phone
-- country code: odometer values (service_orders.odometer_reading, invoices.next_service_km) are
-- now stored and served in whole METERS, and the client converts to the org's unit.
ALTER TABLE organizations ADD COLUMN distance_unit VARCHAR(2) NOT NULL DEFAULT 'KM';
-- Existing values were kilometres; capped at INT max so an absurd legacy value cannot overflow.
UPDATE service_orders SET odometer_reading = LEAST(odometer_reading * 1000.0, 2147483647) WHERE odometer_reading IS NOT NULL;
UPDATE invoices SET next_service_km = LEAST(next_service_km * 1000.0, 2147483647) WHERE next_service_km IS NOT NULL;
--rollback UPDATE invoices SET next_service_km = next_service_km / 1000 WHERE next_service_km IS NOT NULL;
--rollback UPDATE service_orders SET odometer_reading = odometer_reading / 1000 WHERE odometer_reading IS NOT NULL;
--rollback ALTER TABLE organizations DROP COLUMN distance_unit;
