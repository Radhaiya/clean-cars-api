--liquibase formatted sql

--changeset liquibase:005-employee-user-link
-- EMPLOYEE IS THE SEAT (see docs/FEATURE-INVITES.md): a plan's max_users caps the
-- org's employee roster, and an accepted invite links a user account to exactly
-- that one employee row.
-- employees gains:
--   email   — the invite address; NULL until the owner wants someone to join the
--             module (a bare roster name stays valid and consumes a seat).
--   user_id — the accepted invitee's users.id; NULL until someone accepts.
--   UNIQUE(org_id, email) — the same address can't be two roster rows in an org
--   (NULL duplicates are allowed in MySQL; only real emails are unique).
-- org_invites gains employee_id — the invite is addressed to one roster row;
-- the FK is nullable and has no cascade: if the employee row is later deleted
-- (EmployeeDeleteService), the log rows survive with employee_id = NULL.
ALTER TABLE employees
  ADD COLUMN email   VARCHAR(255) NULL AFTER name,
  ADD COLUMN user_id BINARY(16) NULL AFTER email,
  ADD UNIQUE KEY uq_employees_org_email (org_id, email),
  ADD UNIQUE KEY uq_employees_user (user_id),
  ADD CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users(id);
ALTER TABLE org_invites
  ADD COLUMN employee_id BINARY(16) NULL AFTER invited_by,
  ADD KEY idx_org_invites_employee (employee_id),
  ADD CONSTRAINT fk_org_invites_employee FOREIGN KEY (employee_id) REFERENCES employees(id);
--rollback ALTER TABLE org_invites DROP FOREIGN KEY fk_org_invites_employee, DROP INDEX idx_org_invites_employee, DROP COLUMN employee_id; ALTER TABLE employees DROP FOREIGN KEY fk_employees_user, DROP INDEX uq_employees_user, DROP INDEX uq_employees_org_email, DROP COLUMN user_id, DROP COLUMN email;
