--liquibase formatted sql

--changeset liquibase:015-amc-plans logicalFilePath:migrations/015-amc-plans.sql
-- AMC (annual maintenance contract) plan templates — see docs/FEATURE-AMC.md.
--   amc_plans         one bundle (name); archived plans cannot be sold.
--   amc_plan_items    the plan's FIXED service names (different names = a different plan).
--   amc_plan_variants a sellable tenure + frequency of a plan.
--   amc_variant_rows  per-service price / tax of a variant (tax is per row, no flat AMC price).
-- Plan capability: subscription_plans.amc_enabled (default on for every plan; flip per plan later).
ALTER TABLE subscription_plans
  ADD COLUMN amc_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE amc_plans (
  id          BINARY(16)   NOT NULL PRIMARY KEY,
  org_id      BINARY(16)   NOT NULL,
  name        VARCHAR(255) NOT NULL,
  archived    BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_amc_plans_org FOREIGN KEY (org_id) REFERENCES organizations (id),
  CONSTRAINT uq_amc_plans_org_name UNIQUE (org_id, name)
);

CREATE TABLE amc_plan_items (
  id            BINARY(16)   NOT NULL PRIMARY KEY,
  plan_id       BINARY(16)   NOT NULL,
  service_name  VARCHAR(255) NOT NULL,
  position      INT          NOT NULL,
  CONSTRAINT fk_amc_plan_items_plan FOREIGN KEY (plan_id) REFERENCES amc_plans (id),
  CONSTRAINT uq_amc_plan_items_name UNIQUE (plan_id, service_name)
);

CREATE TABLE amc_plan_variants (
  id               BINARY(16) NOT NULL PRIMARY KEY,
  plan_id          BINARY(16) NOT NULL,
  org_id           BINARY(16) NOT NULL,
  tenure_months    INT        NOT NULL,
  interval_months  INT        NOT NULL,
  archived         BOOLEAN    NOT NULL DEFAULT FALSE,
  created_at       TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_amc_variants_plan FOREIGN KEY (plan_id) REFERENCES amc_plans (id),
  CONSTRAINT uq_amc_variants_shape UNIQUE (plan_id, tenure_months, interval_months),
  CONSTRAINT ck_amc_variants_multiple CHECK (tenure_months > 0 AND interval_months > 0 AND tenure_months % interval_months = 0)
);

CREATE TABLE amc_variant_rows (
  id              BINARY(16)    NOT NULL PRIMARY KEY,
  variant_id      BINARY(16)    NOT NULL,
  plan_item_id    BINARY(16)    NOT NULL,
  price           DECIMAL(12,2) NOT NULL,
  tax_percentage  DECIMAL(5,2)  NULL,
  tax_included    BOOLEAN       NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_amc_rows_variant FOREIGN KEY (variant_id) REFERENCES amc_plan_variants (id),
  CONSTRAINT fk_amc_rows_item FOREIGN KEY (plan_item_id) REFERENCES amc_plan_items (id),
  CONSTRAINT uq_amc_rows_item UNIQUE (variant_id, plan_item_id)
);

--rollback DROP TABLE amc_variant_rows; DROP TABLE amc_plan_variants; DROP TABLE amc_plan_items; DROP TABLE amc_plans; ALTER TABLE subscription_plans DROP COLUMN amc_enabled;
