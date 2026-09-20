-- ============================================================
-- CLEANCARS — MySQL schema, generated from cleancars_schema.dbml
-- Multi-tenant garage management. Tenant boundary = organizations.
-- ============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------- ACCOUNTS & TENANCY ----------------

CREATE TABLE subscription_plans (
  id                       INT AUTO_INCREMENT PRIMARY KEY,
  name                     VARCHAR(255) NOT NULL,
  -- Razorpay owns price/currency/billing interval. Each column holds one Razorpay
  -- Plan ID (one per billing cycle — Razorpay plans are per-cycle entities); NULL
  -- = that cycle is not offered. Trial has both NULL (never sold).
  -- Prices are fetched live from Razorpay at the API boundary — never stored here.
  razorpay_monthly_plan_id VARCHAR(100) NULL UNIQUE,
  razorpay_yearly_plan_id  VARCHAR(100) NULL UNIQUE,
  is_trial                 BOOLEAN NOT NULL DEFAULT FALSE,  -- true for exactly one row: the dedicated, one-time trial plan
  max_users            INT NULL,                        -- null = unlimited (hard limit)
  max_cars             INT NULL,                        -- null = unlimited (soft limit)
  report_window_months INT NULL,                        -- null = unlimited
  stats_range_years    INT NULL,                        -- null = unlimited; 0 = stats page hidden
  invoice_generation   BOOLEAN NOT NULL DEFAULT FALSE,
  is_public            BOOLEAN NOT NULL DEFAULT TRUE,   -- hide a plan without deleting it
  sort_order           INT NOT NULL DEFAULT 0,          -- display order on the pricing page
  created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tenant root. A user links here via users.org_id; the owner is the member whose role = 'owner'.
CREATE TABLE organizations (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(255) NOT NULL,
  contact_phone VARCHAR(255),
  contact_email VARCHAR(255),
  tagline       VARCHAR(255) NULL,
  address_line1 VARCHAR(255) NULL,
  address_line2 VARCHAR(255) NULL,
  state         VARCHAR(255) NULL,
  country       VARCHAR(255) NULL,
  zip_code      VARCHAR(255) NULL,
  -- IANA zone id (e.g. 'Asia/Kolkata') — the org's display timezone; API timestamps
  -- are stored UTC and converted to this zone on the way out. Compulsory when an
  -- org is created (StartTrialRequest); the DEFAULT only backfills pre-existing rows.
  timezone      VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  org_id        INT NULL,
  name          VARCHAR(255) NOT NULL,
  email         VARCHAR(255) NULL UNIQUE,        -- null for phone-only (OTP) sign-ins
  phone         VARCHAR(255) NULL,
  firebase_uid  VARCHAR(128) NULL UNIQUE,        -- the real identity key; email/phone are just contact info
  role          ENUM('owner','admin','staff') NOT NULL DEFAULT 'staff',
  status        ENUM('invited','active','disabled') NOT NULL DEFAULT 'invited',
  trial_used    BOOLEAN NOT NULL DEFAULT FALSE,   -- account's one-time free trial consumed (keyed by the unique email)
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_users_org_id (org_id),
  CONSTRAINT fk_users_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

CREATE TABLE subscriptions (
  id                INT AUTO_INCREMENT PRIMARY KEY,
  org_id            INT NOT NULL,
  plan_id           INT NOT NULL,
  status            ENUM('trialing','pending','active','past_due','halted','cancelled','expired') NOT NULL DEFAULT 'trialing',
  start_date        DATE,
  end_date          DATE,
  payment_reference VARCHAR(255) NULL,
  -- Razorpay subscription reference for paid rows (NULL while trialing / expired-local rows).
  -- The billing cycle sold: set at subscribe time (the Razorpay plan id chosen encodes it);
  -- NULL while trialing (no Razorpay plan).
  billing_cycle     ENUM('monthly','yearly') NULL AFTER razorpay_subscription_id,
  razorpay_subscription_id VARCHAR(100) NULL UNIQUE,
  -- Autopay method the Razorpay subscription runs on (card/upi/...), from the
  -- activation webhook; changePlan rejects UPI mandates up-front (Razorpay 400s them).
  payment_method    VARCHAR(50) NULL,
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_subscriptions_org_id (org_id),
  CONSTRAINT fk_subscriptions_org  FOREIGN KEY (org_id)  REFERENCES organizations(id),
  CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)
);

CREATE TABLE org_invites (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  org_id      INT NOT NULL,
  email       VARCHAR(255) NOT NULL,
  invited_by  INT NOT NULL,
  role        ENUM('owner','admin','staff') NOT NULL DEFAULT 'staff',
  token       VARCHAR(255) NOT NULL,
  status      ENUM('pending','accepted','expired','revoked') NOT NULL DEFAULT 'pending',
  expires_at  TIMESTAMP NOT NULL,
  accepted_at TIMESTAMP NULL,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_org_invites_token (token),
  KEY idx_org_invites_org_email (org_id, email),
  CONSTRAINT fk_org_invites_org        FOREIGN KEY (org_id)     REFERENCES organizations(id),
  CONSTRAINT fk_org_invites_invited_by FOREIGN KEY (invited_by) REFERENCES users(id)
);

-- ---------------- CUSTOMER ----------------

CREATE TABLE customers (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  org_id        INT NOT NULL,
  name          VARCHAR(255) NOT NULL,
  phone         VARCHAR(255) NOT NULL,
  alt_phone     VARCHAR(255) NULL,
  email         VARCHAR(255) NULL,
  address       VARCHAR(255) NULL,
  notes         TEXT NULL,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_customers_org_id (org_id),
  UNIQUE KEY uq_customers_org_phone (org_id, phone),
  CONSTRAINT fk_customers_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

-- ---------------- CAR CATALOG (per-org) ----------------

CREATE TABLE car_brands (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  org_id     INT NOT NULL,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_car_brands_org_name (org_id, name),
  CONSTRAINT fk_car_brands_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

CREATE TABLE car_models (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  org_id     INT NOT NULL,
  brand_id   INT NOT NULL,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_car_models_org_brand_name (org_id, brand_id, name),
  CONSTRAINT fk_car_models_org   FOREIGN KEY (org_id)   REFERENCES organizations(id),
  CONSTRAINT fk_car_models_brand FOREIGN KEY (brand_id) REFERENCES car_brands(id)
);

CREATE TABLE cars (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  org_id      INT NOT NULL,
  customer_id INT NOT NULL,
  car_number  VARCHAR(255) NOT NULL,
  brand_id    INT NULL,
  model_id    INT NULL,
  year        INT NULL,
  color       VARCHAR(255) NULL,
  fuel_type   ENUM('petrol','diesel','electric','hybrid','cng','lpg') NULL,
  chassis_vin VARCHAR(255) NULL,
  comments    TEXT NULL,
  created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cars_org_id (org_id),
  KEY idx_cars_org_number (org_id, car_number),
  KEY idx_cars_customer_id (customer_id),
  CONSTRAINT fk_cars_org      FOREIGN KEY (org_id)      REFERENCES organizations(id),
  CONSTRAINT fk_cars_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
  CONSTRAINT fk_cars_brand    FOREIGN KEY (brand_id)    REFERENCES car_brands(id),
  CONSTRAINT fk_cars_model    FOREIGN KEY (model_id)    REFERENCES car_models(id)
);

-- ---------------- SERVICES ----------------

CREATE TABLE service_categories (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  org_id     INT NOT NULL,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_service_categories_org_name (org_id, name),
  CONSTRAINT fk_service_categories_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

CREATE TABLE service_catalog (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  org_id         INT NOT NULL,
  name           VARCHAR(255) NOT NULL,
  category_id    INT NULL,                         -- optional; ON DELETE SET NULL keeps the service, just uncategorized
  default_price  DECIMAL(12,2) NOT NULL,           -- base price only; net/GST/gross are computed in code
  gst_percentage DECIMAL(5,2) NULL,                -- GST rate, e.g. 18.00; NULL = GST not applicable
  gst_included   BOOLEAN NOT NULL DEFAULT FALSE,   -- TRUE = default_price already includes GST
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_service_catalog_org_name (org_id, name),
  KEY idx_service_catalog_category (category_id),
  CONSTRAINT fk_service_catalog_org      FOREIGN KEY (org_id)      REFERENCES organizations(id),
  CONSTRAINT fk_service_catalog_category FOREIGN KEY (category_id) REFERENCES service_categories(id) ON DELETE SET NULL
);

CREATE TABLE vendors (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  org_id        INT NOT NULL,
  name          VARCHAR(255) NOT NULL,
  contact_phone VARCHAR(255) NULL,
  address       VARCHAR(255) NULL,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_vendors_org_id (org_id),
  CONSTRAINT fk_vendors_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

CREATE TABLE service_orders (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  org_id           INT NOT NULL,
  car_id           INT NOT NULL,
  customer_id      INT NOT NULL,                   -- derived from the car's owner, snapshotted here
  created_by       INT NOT NULL,                   -- users.id, from the token
  employee_id      INT NULL,                       -- employees.id, the staff member on the job
  odometer_reading INT NULL,
  vendor_id        INT NULL,                        -- outside garage for the whole job; non-null => outsourced
  status           ENUM('in_progress','completed','cancelled') NOT NULL DEFAULT 'in_progress',
  paid             BOOLEAN NOT NULL DEFAULT FALSE, -- independent of payment_date
  payment_date     DATE NULL,                      -- independent of paid; free to set either way
  payment_type     ENUM('card','cash','upi') NULL, -- how it was paid; null until recorded
  notes            TEXT NULL,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  completed_at     TIMESTAMP NULL,
  KEY idx_service_orders_org_id (org_id),
  KEY idx_service_orders_car_id (car_id),
  KEY idx_service_orders_customer_id (customer_id),
  KEY idx_service_orders_status (status),
  KEY idx_service_orders_vendor_id (vendor_id),
  CONSTRAINT fk_service_orders_org        FOREIGN KEY (org_id)      REFERENCES organizations(id),
  CONSTRAINT fk_service_orders_car        FOREIGN KEY (car_id)      REFERENCES cars(id),
  CONSTRAINT fk_service_orders_customer   FOREIGN KEY (customer_id) REFERENCES customers(id),
  CONSTRAINT fk_service_orders_created_by FOREIGN KEY (created_by)  REFERENCES users(id),
  CONSTRAINT fk_service_orders_employee   FOREIGN KEY (employee_id) REFERENCES employees(id),
  CONSTRAINT fk_service_orders_vendor     FOREIGN KEY (vendor_id)   REFERENCES vendors(id)
);

-- Snapshot lines. A catalog service is only read at add-time to seed these values;
-- nothing here links back to service_catalog. base_price / gst_* are freely editable
-- per line (e.g. a customer-specific price). Net/GST/gross are computed in code.
CREATE TABLE service_order_items (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  service_order_id INT NOT NULL,
  service_name     VARCHAR(255) NOT NULL,
  base_price       DECIMAL(12,2) NOT NULL,
  gst_percentage   DECIMAL(5,2) NULL,
  gst_included     BOOLEAN NOT NULL DEFAULT FALSE,
  quantity         INT NOT NULL DEFAULT 1,
  notes            TEXT NULL,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_service_order_items_order (service_order_id),
  CONSTRAINT fk_service_order_items_order FOREIGN KEY (service_order_id) REFERENCES service_orders(id)
);

CREATE TABLE payments (
  id               INT AUTO_INCREMENT PRIMARY KEY,
  service_order_id INT NOT NULL,
  amount           DECIMAL(12,2) NOT NULL,
  mode             ENUM('cash','card','upi','netbanking','cheque','wallet','credit') NOT NULL,
  reference_number VARCHAR(255) NULL,
  paid_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  received_by      INT NULL,
  created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_payments_order (service_order_id),
  CONSTRAINT fk_payments_order       FOREIGN KEY (service_order_id) REFERENCES service_orders(id),
  CONSTRAINT fk_payments_received_by FOREIGN KEY (received_by)      REFERENCES users(id)
);

CREATE TABLE employees (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  org_id     INT NOT NULL,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_employees_org_id (org_id),
  CONSTRAINT fk_employees_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

-- ---------------- EXPENSES ----------------

CREATE TABLE expense_categories (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  org_id     INT NOT NULL,
  name       VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_expense_categories_org_name (org_id, name),
  CONSTRAINT fk_expense_categories_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

CREATE TABLE expenses (
  id             INT AUTO_INCREMENT PRIMARY KEY,
  org_id         INT NOT NULL,
  category_name  VARCHAR(255) NOT NULL,           -- denormalized label, no FK: deleting the category only removes it from the dropdown, past expenses keep the name
  amount         DECIMAL(12,2) NOT NULL,           -- unit amount only; net/GST/gross are computed in code
  gst_percentage DECIMAL(5,2) NULL,                -- GST rate, e.g. 18.00; NULL = GST not applicable
  gst_included   BOOLEAN NOT NULL DEFAULT FALSE,   -- TRUE = amount already includes GST
  quantity       INT NOT NULL DEFAULT 1,
  notes          TEXT NULL,
  created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_expenses_org_id (org_id),
  KEY idx_expenses_category_name (category_name),
  CONSTRAINT fk_expenses_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

-- ---------------- RAZORPAY BILLING ----------------

-- Money actually charged, one row per Razorpay payment (append-only snapshots —
-- Razorpay owns pricing; this records what was actually charged and when).
-- Named razorpay_payments — `payments` is the garage's own service-order receipts.
CREATE TABLE razorpay_payments (
  id                      INT AUTO_INCREMENT PRIMARY KEY,
  subscription_id         INT NOT NULL,                    -- local subscriptions.id
  razorpay_payment_id     VARCHAR(100) NOT NULL,
  razorpay_order_id       VARCHAR(100) NULL,
  razorpay_invoice_id     VARCHAR(100) NULL,
  amount                  BIGINT NOT NULL,                 -- Razorpay raw amount (paise)
  currency                VARCHAR(10) NOT NULL,
  status                  ENUM('created','authorized','captured','failed','refunded') NOT NULL,
  paid_at                 TIMESTAMP NULL,
  created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_razorpay_payments_razorpay_payment_id (razorpay_payment_id),
  KEY idx_razorpay_payments_subscription_id (subscription_id),
  CONSTRAINT fk_razorpay_payments_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions(id)
);

-- Append-only audit of every meaningful Razorpay webhook. razorpay_event_id is the
-- idempotency key (duplicate deliveries return 200 without reprocessing).
CREATE TABLE payment_events (
  id                      INT AUTO_INCREMENT PRIMARY KEY,
  razorpay_event_id       VARCHAR(150) NOT NULL,
  event_type              VARCHAR(100) NOT NULL,
  razorpay_subscription_id VARCHAR(100) NULL,
  razorpay_payment_id     VARCHAR(100) NULL,
  processing_status       ENUM('received','processed','ignored','failed') NOT NULL DEFAULT 'received',
  payload_json            JSON NOT NULL,
  error_message           TEXT NULL,
  received_at             TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  processed_at            TIMESTAMP NULL,
  UNIQUE KEY uq_payment_events_razorpay_event_id (razorpay_event_id),
  KEY idx_payment_events_razorpay_subscription (razorpay_subscription_id)
);

-- ---------------- AUTH ----------------

-- Rotating, revocable refresh tokens. Only the SHA-256 hash of the token is stored.
CREATE TABLE refresh_tokens (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  user_id    INT NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_refresh_tokens_token_hash (token_hash),
  KEY idx_refresh_tokens_user_id (user_id),
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

SET FOREIGN_KEY_CHECKS = 1;
