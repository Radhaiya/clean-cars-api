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
-- Prices live in Razorpay (fetched live at the API boundary) — each plan
-- needs a Razorpay Plan ID per billing cycle created in the Razorpay
-- Dashboard, then filled into razorpay_monthly_plan_id / razorpay_yearly_plan_id
-- below (leave NULL for a cycle the plan doesn't offer; TODO(razorpay): fill in
-- the 8 real plan IDs). Trial has both NULL — never sold.
-- Trial: all features unlocked like Enterprise, but at reduced limits, free,
-- one-time use per account (see SubscriptionService.TRIAL_DAYS / users.trial_used).
-- ------------------------------------------------------------
INSERT INTO subscription_plans
  (name, razorpay_monthly_plan_id, razorpay_yearly_plan_id, is_trial, max_users, max_cars,
   report_window_months, stats_range_years, invoice_generation, is_public, sort_order)
VALUES
  ('Trial',      NULL, NULL, TRUE,  2,    50,   1,    1,    TRUE,  TRUE, 0),
  ('Starter',    NULL, NULL, FALSE, 2,    200,  1,    0,    FALSE, TRUE, 1),
  ('Workshop',   NULL, NULL, FALSE, 5,    1000, 4,    1,    FALSE, TRUE, 2),
  ('Pro',        NULL, NULL, FALSE, 15,   5000, 12,   3,    FALSE, TRUE, 3),
  ('Enterprise', NULL, NULL, FALSE, NULL, NULL, NULL, NULL, TRUE,  TRUE, 4);
