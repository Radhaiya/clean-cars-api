--liquibase formatted sql

--changeset liquibase:019-otp-verification-flag logicalFilePath:migrations/019-otp-verification-flag.sql
-- Client.Otp.Verification.Required — when true (the shipped behavior) a verified
-- phone is compulsory to buy a plan and Firebase email must be verified at login.
-- Seeded on so nothing changes until the console turns it off.
INSERT INTO feature_properties (id, property_key, property_value, description)
SELECT UNHEX(REPLACE(UUID(), '-', '')), 'Client.Otp.Verification.Required', CAST('true' AS JSON),
       'When true phone OTP (to buy a plan) and verified email (at login) are compulsory'
WHERE NOT EXISTS (SELECT 1 FROM feature_properties WHERE property_key = 'Client.Otp.Verification.Required');
--rollback DELETE FROM feature_properties WHERE property_key = 'Client.Otp.Verification.Required';
