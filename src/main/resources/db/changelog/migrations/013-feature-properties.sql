--liquibase formatted sql

--changeset liquibase:013-feature-properties logicalFilePath:migrations/013-feature-properties.sql
-- feature_properties — runtime switches the internal console flips and the apps
-- read. One row per key; the value is a JSON document so a property can hold a
-- boolean, string, number or object without a schema change.
--   Keys prefixed "Client." are public: GET /api/properties serves them to the
--   main UI before login. Everything else stays console-only.
CREATE TABLE IF NOT EXISTS feature_properties (
    id               BINARY(16)   NOT NULL PRIMARY KEY,
    property_key     VARCHAR(128) NOT NULL,
    property_value   JSON         NOT NULL,
    description      VARCHAR(255) NULL,
    updated_by_email VARCHAR(255) NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_feature_properties_key UNIQUE (property_key)
) ENGINE=InnoDB;

-- Seed: the two switches the product ships with, both off. The documented
-- exception to the "no seed data" rule (like allowed_emails in 006) — the
-- backend and UI read these keys, so the rows must exist from the first boot.
INSERT INTO feature_properties (id, property_key, property_value, description)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'Client.Maintenance.Mode.Enable', CAST('false' AS JSON),
       'When true the apps show a full-screen maintenance page and /api/** answers 503'
WHERE NOT EXISTS (SELECT 1 FROM feature_properties WHERE property_key = 'Client.Maintenance.Mode.Enable');

INSERT INTO feature_properties (id, property_key, property_value, description)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'Client.New.Logins.Disabled', CAST('false' AS JSON),
       'When true brand-new users cannot sign in (existing users still can)'
WHERE NOT EXISTS (SELECT 1 FROM feature_properties WHERE property_key = 'Client.New.Logins.Disabled');
--rollback DROP TABLE feature_properties;
