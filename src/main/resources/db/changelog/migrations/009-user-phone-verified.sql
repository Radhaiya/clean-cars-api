--liquibase formatted sql

--changeset liquibase:009-user-phone-verified
-- Twilio Verify phone verification on the user ACCOUNT (the person who logs in
-- and buys a plan). POST /api/me/phone/start sends an OTP over SMS or WhatsApp;
-- an approved check persists the verified number onto users.phone and flips
-- phone_verified. POST /api/subscription/subscribe refuses unverified owners
-- (409 phone_verification_required). Existing rows default to false — there is
-- no verification history to trust for backfilled data.
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN phone_verified_at DATETIME NULL;
--rollback ALTER TABLE users DROP COLUMN phone_verified_at;
--rollback ALTER TABLE users DROP COLUMN phone_verified;
