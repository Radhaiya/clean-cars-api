--liquibase formatted sql

--changeset liquibase:023-user-email-verified logicalFilePath:migrations/023-user-email-verified.sql
-- Email verification of the user ACCOUNT via a 6-digit OTP sent through Brevo
-- (POST /api/me/email/start -> /check). Only a SHA-256 hash of the code is stored,
-- with its expiry, send time (resend cooldown) and a wrong-attempt counter.
-- Existing rows default to unverified.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verified_at DATETIME NULL;
ALTER TABLE users ADD COLUMN email_otp_hash CHAR(64) NULL;
ALTER TABLE users ADD COLUMN email_otp_sent_at DATETIME NULL;
ALTER TABLE users ADD COLUMN email_otp_expires_at DATETIME NULL;
ALTER TABLE users ADD COLUMN email_otp_attempts INT NOT NULL DEFAULT 0;
--rollback ALTER TABLE users DROP COLUMN email_otp_attempts;
--rollback ALTER TABLE users DROP COLUMN email_otp_expires_at;
--rollback ALTER TABLE users DROP COLUMN email_otp_sent_at;
--rollback ALTER TABLE users DROP COLUMN email_otp_hash;
--rollback ALTER TABLE users DROP COLUMN email_verified_at;
--rollback ALTER TABLE users DROP COLUMN email_verified;
