-- ============================================================
-- Seed data. Sign-in is Firebase-only (no password) — these rows exist so the
-- rest of the app has org/subscription data to read; log in as them via
-- POST /api/auth/firebase with a real Firebase ID token for these same emails.
--   test@gmail.com -> owner of org "test-123"
--   test_free@gmail.com -> no org yet (org is created when they start a trial / buy a plan)
-- ============================================================

-- users.org_id -> organizations.id is now the only link between the two tables,
-- so insert the org first, then the user.
INSERT INTO organizations (id, name, contact_email, timezone)
VALUES (1, 'test-123', 'test@gmail.com', 'Asia/Kolkata');

INSERT INTO users (id, org_id, name, email, phone, role, status)
VALUES (1, 1, 'test', 'test@gmail.com', NULL, 'owner', 'active');

-- Org-less account for testing the sign-up -> start-trial flow.
INSERT INTO users (id, org_id, name, email, phone, role, status)
VALUES (2, NULL, 'test free', 'test_free@gmail.com', NULL, 'staff', 'active');

-- ------------------------------------------------------------
-- Subscription plan catalogue: the dedicated Trial plan, then P1 -> P4.
-- Prices are manual INR until Cashfree is wired (see CLAUDE.md
-- "Subscription plans & billing"). yearly_price = 10x monthly (~2 months free).
-- Trial: all features unlocked like Enterprise, but at reduced limits, free,
-- one-time use per account (see SubscriptionService.TRIAL_DAYS / users.trial_used).
-- ------------------------------------------------------------
INSERT INTO subscription_plans
  (name, monthly_price, yearly_price, is_trial, max_users, max_cars,
   report_window_months, stats_range_years, invoice_generation, is_public, sort_order)
VALUES
  ('Trial',      0.00,    NULL,     TRUE,  2,    50,   1,    1,    TRUE,  TRUE, 0),
  ('Starter',    499.00,  4990.00,  FALSE, 2,    200,  1,    0,    FALSE, TRUE, 1),
  ('Workshop',   999.00,  9990.00,  FALSE, 5,    1000, 4,    1,    FALSE, TRUE, 2),
  ('Pro',        1999.00, 19990.00, FALSE, 15,   5000, 12,   3,    FALSE, TRUE, 3),
  ('Enterprise', 4999.00, 49990.00, FALSE, NULL, NULL, NULL, NULL, TRUE,  TRUE, 4);
