--liquibase formatted sql

--changeset liquibase:004-invite-roles
-- ORG INVITES feature role extension (see docs/FEATURE-INVITES.md):
-- users.role / org_invites.role gain 'manager' and 'worker' (invite-granted roles;
-- OWNER stays the org creator's role, only owners can send invites; the legacy
-- 'admin'/'staff' values remain valid until the role model is revisited).
-- org_invites.status gains 'declined' — the invitee's own deny action, distinct
-- from 'revoked' (sender-side cancel). No data backfill: existing rows keep their
-- values, and org_invites has no Java code behind it yet (built now).
ALTER TABLE users
  MODIFY role ENUM('owner','admin','staff','manager','worker') NOT NULL DEFAULT 'worker';
ALTER TABLE org_invites
  MODIFY role   ENUM('owner','admin','staff','manager','worker') NOT NULL DEFAULT 'worker',
  MODIFY status ENUM('pending','accepted','expired','revoked','declined') NOT NULL DEFAULT 'pending';
--rollback ALTER TABLE org_invites MODIFY role ENUM('owner','admin','staff') NOT NULL DEFAULT 'staff', MODIFY status ENUM('pending','accepted','expired','revoked') NOT NULL DEFAULT 'pending'; ALTER TABLE users MODIFY role ENUM('owner','admin','staff') NOT NULL DEFAULT 'staff';
