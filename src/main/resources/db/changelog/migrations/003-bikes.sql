--liquibase formatted sql

--changeset liquibase:003-bike-catalog
-- BIKE CATALOG (per-org) — a 1:1 mirror of the car catalog:
-- bike_brands / bike_models / bikes. Bikes link to a customer exactly like
-- cars; bike_number is intentionally non-unique (plates get reassigned);
-- fuel enum is the bike-appropriate subset (no diesel/hybrid); both
-- chassis_number and engine_number are optional and non-unique.
CREATE TABLE bike_brands (
  id         BINARY(16) PRIMARY KEY,
  org_id     BINARY(16) NOT NULL,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_bike_brands_org_name (org_id, name),
  CONSTRAINT fk_bike_brands_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

CREATE TABLE bike_models (
  id         BINARY(16) PRIMARY KEY,
  org_id     BINARY(16) NOT NULL,
  brand_id   BINARY(16) NOT NULL,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_bike_models_org_brand_name (org_id, brand_id, name),
  CONSTRAINT fk_bike_models_org   FOREIGN KEY (org_id)   REFERENCES organizations(id),
  CONSTRAINT fk_bike_models_brand FOREIGN KEY (brand_id) REFERENCES bike_brands(id)
);

CREATE TABLE bikes (
  id             BINARY(16) PRIMARY KEY,
  org_id         BINARY(16) NOT NULL,
  customer_id    BINARY(16) NOT NULL,
  bike_number    VARCHAR(255) NOT NULL,
  brand_id       BINARY(16) NULL,
  model_id       BINARY(16) NULL,
  year           INT NULL,
  color          VARCHAR(255) NULL,
  fuel_type      ENUM('petrol','electric','cng','lpg') NULL,
  chassis_number VARCHAR(255) NULL,
  engine_number  VARCHAR(255) NULL,
  comments       TEXT NULL,
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_bikes_org_id (org_id),
  KEY idx_bikes_org_number (org_id, bike_number),
  KEY idx_bikes_customer_id (customer_id),
  CONSTRAINT fk_bikes_org      FOREIGN KEY (org_id)      REFERENCES organizations(id),
  CONSTRAINT fk_bikes_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
  CONSTRAINT fk_bikes_brand    FOREIGN KEY (brand_id)    REFERENCES bike_brands(id),
  CONSTRAINT fk_bikes_model    FOREIGN KEY (model_id)    REFERENCES bike_models(id)
);
--rollback DROP TABLE bikes; DROP TABLE bike_models; DROP TABLE bike_brands;

--changeset liquibase:003-bike-service-orders
-- Service orders gain a bike alongside the car (exactly one of the two is set,
-- enforced app-side). Existing rows keep their car_id; the column only widens.
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'service_orders' AND column_name = 'bike_id'
ALTER TABLE service_orders
  MODIFY car_id BINARY(16) NULL,
  ADD COLUMN bike_id BINARY(16) NULL AFTER car_id;
--rollback ALTER TABLE service_orders DROP COLUMN bike_id;

--changeset liquibase:003-bike-service-orders-index
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'service_orders' AND column_name = 'bike_id'
ALTER TABLE service_orders
  ADD KEY idx_service_orders_bike_id (bike_id),
  ADD CONSTRAINT fk_service_orders_bike FOREIGN KEY (bike_id) REFERENCES bikes(id);
--rollback ALTER TABLE service_orders DROP FOREIGN KEY fk_service_orders_bike, DROP INDEX idx_service_orders_bike_id;
