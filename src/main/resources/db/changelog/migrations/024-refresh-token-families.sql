--liquibase formatted sql

--changeset liquibase:024-refresh-token-families logicalFilePath:migrations/024-refresh-token-families.sql
-- Refresh-token families: every login starts a family and each rotation stays in it, so a
-- replayed (stolen / badly retried) token revokes only its own family instead of every
-- session the user has. family_started_at backs the absolute session lifetime;
-- replaced_by records which token superseded this one (forensics). Existing rows become
-- single-token families.
ALTER TABLE refresh_tokens ADD COLUMN family_id BINARY(16) NULL;
ALTER TABLE refresh_tokens ADD COLUMN family_started_at TIMESTAMP NULL;
ALTER TABLE refresh_tokens ADD COLUMN replaced_by BINARY(16) NULL;
UPDATE refresh_tokens SET family_id = id, family_started_at = COALESCE(created_at, CURRENT_TIMESTAMP);
ALTER TABLE refresh_tokens MODIFY family_id BINARY(16) NOT NULL;
ALTER TABLE refresh_tokens MODIFY family_started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
--rollback DROP INDEX idx_refresh_tokens_family_id ON refresh_tokens;
--rollback ALTER TABLE refresh_tokens DROP COLUMN replaced_by;
--rollback ALTER TABLE refresh_tokens DROP COLUMN family_started_at;
--rollback ALTER TABLE refresh_tokens DROP COLUMN family_id;
