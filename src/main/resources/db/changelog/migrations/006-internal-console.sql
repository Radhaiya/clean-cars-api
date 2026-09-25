--liquibase formatted sql

--changeset liquibase:006-internal-console
-- INTERNAL CONSOLE (built into clean-cars-api — one process serving /api and
-- /internal/api): the console's own two tables. It writes ONLY these; every
-- tenant-read endpoint is query-only against the main-app tables, which this
-- migration never touches.
--
-- allowed_emails — the single source of truth for "can log in to the console".
--   Identity key is the lowercased email from the Firebase ID token; anything
--   not on the list is rejected at login with 403 email_not_whitelisted.
-- internal_refresh_tokens — same opaque-token design as refresh_tokens, but
--   keyed by email (a console user need not correspond to any users row).
CREATE TABLE IF NOT EXISTS allowed_emails (
    id               BINARY(16)   NOT NULL PRIMARY KEY,
    email            VARCHAR(255) NOT NULL,
    created_by_email VARCHAR(255) NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_allowed_emails_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS internal_refresh_tokens (
    id          BINARY(16)   NOT NULL PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    firebase_uid VARCHAR(128) NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    revoked_at  TIMESTAMP    NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_internal_refresh_token_hash UNIQUE (token_hash)
) ENGINE=InnoDB;

-- Seed bootstrap: the first console user. The deliberate, documented exception
-- to the "no seed data" rule — without it nobody could ever log in or add
-- anyone else to the list. No-op when the row already exists (e.g. a sibling
-- console tooling migration seeded the same bootstrap row on this shared DB).
INSERT INTO allowed_emails (id, email)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'radhaiya.solutions@gmail.com'
WHERE NOT EXISTS (SELECT 1 FROM allowed_emails WHERE email = 'radhaiya.solutions@gmail.com');
--rollback DROP TABLE internal_refresh_tokens; DROP TABLE allowed_emails;
