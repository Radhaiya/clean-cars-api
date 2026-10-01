--liquibase formatted sql

--changeset liquibase:016-amc-subscriptions logicalFilePath:migrations/016-amc-subscriptions.sql
-- A sold AMC (docs/FEATURE-AMC.md): belongs to ONE car or bike (no customer), holds a full snapshot of the
-- variant it was sold from, and records the single upfront payment (AMC revenue, separate from service
-- revenue). Used / lapsed / remaining are NOT stored — they are computed from start_date, tenure and
-- the redemption orders. No cancellation / refund.
CREATE TABLE amc_subscriptions (
  id                    BINARY(16)    NOT NULL PRIMARY KEY,
  org_id                BINARY(16)    NOT NULL,
  car_id                BINARY(16)    NULL,
  bike_id               BINARY(16)    NULL,
  plan_id               BINARY(16)    NOT NULL,
  variant_id            BINARY(16)    NOT NULL,
  plan_name             VARCHAR(255)  NOT NULL,
  tenure_months         INT           NOT NULL,
  interval_months       INT           NOT NULL,
  start_date            DATE          NOT NULL,
  sale_net              DECIMAL(12,2) NOT NULL,
  sale_tax              DECIMAL(12,2) NOT NULL,
  sale_gross            DECIMAL(12,2) NOT NULL,
  payment_mode          ENUM('card','cash','upi') NOT NULL,
  payment_date          DATE          NOT NULL,
  received_by           BINARY(16)    NULL,
  sold_by_employee_id   BINARY(16)    NULL,
  created_at            TIMESTAMP(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_amc_subs_org FOREIGN KEY (org_id) REFERENCES organizations (id),
  CONSTRAINT fk_amc_subs_car FOREIGN KEY (car_id) REFERENCES cars (id),
  CONSTRAINT fk_amc_subs_bike FOREIGN KEY (bike_id) REFERENCES bikes (id),
  CONSTRAINT fk_amc_subs_plan FOREIGN KEY (plan_id) REFERENCES amc_plans (id),
  CONSTRAINT fk_amc_subs_variant FOREIGN KEY (variant_id) REFERENCES amc_plan_variants (id),
  CONSTRAINT fk_amc_subs_employee FOREIGN KEY (sold_by_employee_id) REFERENCES employees (id),
  CONSTRAINT ck_amc_subs_one_vehicle CHECK ((car_id IS NULL) <> (bike_id IS NULL)),
  KEY idx_amc_subs_org_car (org_id, car_id),
  KEY idx_amc_subs_org_bike (org_id, bike_id)
);

CREATE TABLE amc_subscription_items (
  id               BINARY(16)    NOT NULL PRIMARY KEY,
  subscription_id  BINARY(16)    NOT NULL,
  service_name     VARCHAR(255)  NOT NULL,
  position         INT           NOT NULL,
  price            DECIMAL(12,2) NOT NULL,
  tax_percentage   DECIMAL(5,2)  NULL,
  tax_included     BOOLEAN       NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_amc_sub_items_sub FOREIGN KEY (subscription_id) REFERENCES amc_subscriptions (id)
);

--rollback DROP TABLE amc_subscription_items; DROP TABLE amc_subscriptions;
