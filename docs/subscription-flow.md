# Subscription & trial flow — how it works end to end

This is the plain-English walkthrough of trials, paid plans, Razorpay webhooks
and what happens when things go wrong. Every step says where the code lives,
what changes in the database, and what the fallback is.

See also: `docs/ARCHITECTURE.md` (conventions) and
`razorpay-saas-billing-entitlements.md` (billing design).

---

## 0. The main pieces

| Piece | Job |
|---|---|
| `subscription_plans` | The catalogue (Trial + 4 paid plans). Limits/features live here; **prices never do** — prices come live from Razorpay. |
| `subscriptions` | One row per "an org once had this deal". An org can have many rows over time; at most one is live. |
| `SubscriptionService` | Trial start, buy plan, plan change, release stuck checkout. |
| `RazorpayWebhookService` | Applies Razorpay's push events to the local rows. |
| `SubscriptionSyncService` | Applies Razorpay's state the other way round — pull, not push. This is the lost-webhook healer. |
| `RazorpayGateway` | The only code that calls Razorpay's REST API. |
| `SubscriptionExpiryJob` | Nightly scheduler, flips finished trials to `EXPIRED`. |
| `PlanLimitService` | One place that answers "what is this org allowed to do" (seats, statistics window). |
| `payment_events` / `razorpay_payments` | Webhook log (dedupe) and payment snapshots. |

**"Live" statuses:** `TRIALING`, `ACTIVE`, `PAST_DUE`.
**All others are not live:** `PENDING` (checkout in flight), `HALTED`,
`CANCELLED`, `EXPIRED`.

Rules that hold everywhere: one account : one org; one trial per account, ever;
append-only history (never update an old row into a new deal — always insert).

---

## 1. The whole journey

```
signup (Firebase)                → user exists, no org, no trial yet
   │
   ▼
POST /api/subscription/trial     → org created, user becomes owner,
(ONE transaction)                  subscriptions row #1 = TRIALING (14 days),
                                   fresh token with the new org_id
   │
   ▼  (trial runs for 14 days; job expires it if not converted)
   ▼
POST /api/subscription/subscribe → 3 small steps, see §4:
   │   1. lock org → validate → save row #2 PENDING → COMMIT
   │   2. call Razorpay (create subscription, expire_by = +30 min)
   │   3. second commit: attach Razorpay id to row #2
   │      (call failed? → row #2 flips CANCELLED, org can retry)
   │
   ▼
Razorpay Checkout → customer pays
   │
   ▼
webhook: subscription.activated / charged
   │   row #2 → ACTIVE, real period dates, plan synced,
   │   trial row #1 → CANCELLED (trial was free, it ends early)
   ▼
UI polls GET /api/subscription/{id}
   │   sees active=true → redirects into the app
   ▼
... charges continue: charged (each cycle), pending→PAST_DUE,
    halted, cancelled, completed/expired, updated (plan change)
```

---

## 2. Starting a trial — `POST /api/subscription/trial`

Caller must be an authenticated user **without an org**. Body:
`{ orgName, timezone, currency, contactPhone?, contactEmail?, address? }`.
One transaction does everything:

1. **Lock the user row** (`SELECT ... FOR UPDATE`). Two racing calls queue up;
   the loser sees the winner's result and gets a 409 instead of double-spending.
2. Guards (clean 409s):
   - `user_already_has_org` — they already have an org.
   - `trial_already_used` — `users.trial_used` is true. One trial per account, ever.
3. Load the one Trial plan (`findByIsTrialTrue`). **If the operator never inserted
   the Trial row, this is a 500** — the DB ships empty on purpose; plans are
   manual rows.
4. Create the org (timezone via `ZoneId`, currency via `ReferenceDataService` —
   bad values → 400), link user as `owner`, set `trial_used`.
5. Insert the trial row: `TRIALING`, `start = today (UTC)`, `end = today + 14`.
   No Razorpay id, no billing cycle — a trial never charged anything.
6. Issue a **fresh token carrying the new `org_id`** (the old token has no org;
   the refresh token keeps working).

Go-wrong cases → outcome:
- Race between two `trial` calls → 2nd gets `user_already_has_org`.
- Trial used before, org-less again → `trial_already_used` (it's on the account, not the org).
- Anything inside fails → whole transaction rolls back; user is back to org-less.
- Trial time is up but the job hasn't run yet → UI shows `daysRemaining=0` with
  `active=true` for a few minutes until §7 fires. Cosmetic only.

---

## 3. Reading state — `GET /api/subscription`

Always 200. Pick, in order:

1. The newest row whose status is live (`TRIALING`/`ACTIVE`/`PAST_DUE`) → report it.
2. Otherwise the newest row of *any* status → report the terminal truth
   (`CANCELLED`/`EXPIRED` with `active=false`, so the UI can show "expired — renew").
3. Otherwise `{active:false, status:"NONE"}`.

`daysRemaining` never goes negative (a lapsed-but-unprocessed trial shows 0).
The embedded `plan` block has **no prices** — prices live in `GET /api/plans`
only, so a subscription read never triggers Razorpay.

---

## 4. Buying a plan — `POST /api/subscription/subscribe`

Owner-only. Body: `{ razorpayPlanId }` — the id picked on the pricing page. The
same id encodes plan **and** billing cycle (monthly column vs yearly column).
Unknown or hidden id → 404.

### 4.1 The order of things (this is the money-safety part)

The Razorpay HTTP call sits **between two short commits**, never inside one big
transaction — if a rollback could happen *after* Razorpay accepted, we'd owe a
customer who never exists in our DB.

```
COMMIT 1 (org row locked):
    1. lock the org row            → two racing subscribes queue, not race
    2. resolve plan, is_public, cycle
    3. blockers check + INSERT row #2 status=PENDING
                                   → org_already_subscribed 409 if blocked

Razorpay (no transaction wrapped around it):
    4. POST /v1/subscriptions
       { plan_id, total_count:100, expire_by: now + 30 min, notes }
COMMIT 2:
    5. write razorpay_subscription_id onto row #2

step 4 failed → COMMIT 2': row #2 → CANCELLED, rethrow
```

Why each piece:

- **Org row lock** — without it, two requests could both pass the "no
  subscription yet" check and create two Razorpay subscriptions (double charge).
  `changePlan` takes the same lock.
- **Blockers** → `org_already_subscribed` (409): `PENDING` (checkout already
  running), `ACTIVE`, `PAST_DUE`. Not blockers: `TRIALING` (trial → paid is
  the happy path), `HALTED`/`CANCELLED`/`EXPIRED` (free to buy again).
- **`expire_by` = 30 min** — if the customer never finishes checkout, Razorpay
  cancels it server-side after 30 min and fires `subscription.cancelled`, which
  clears our `PENDING` row (§5). Without this, an abandoned checkout would block
  buying forever.
- **Razorpay call fails** (network, 4xx, 5xx): the fallback commit cancels the
  local row, so the org can immediately retry. History preserved.

### 4.2 Response

The local `subscriptionId` (the UI's polling key), the `razorpaySubscriptionId`,
the plan id, and our public `keyId` — everything Checkout needs.

### 4.3 How an opened checkout resolves

| What the customer does | How the local row resolves |
|---|---|
| Pays normally | Webhook → ACTIVE (§5), or self-heal on poll (§6) if the webhook is lost. |
| Abandons checkout | Razorpay auto-cancels at `expire_by` → their webhook clears the row. |
| UI stuck > 10 min | `POST /api/subscription/cancel-checkout` (owner) → reconcile-first release, see §6c. |
| Razorpay call failed at step 4 | Already handled — row is `CANCELLED`, org retries. |

---

## 5. Webhooks — `POST /api/webhooks/razorpay` (Razorpay pushing at us)

Permit-all on purpose; the `X-Razorpay-Signature` HMAC is the auth (401 before
anything else). Razorpay pushes **at least once** per event, maybe twice:

- Same event delivered again and it's `PROCESSED`/`IGNORED` → 200, nothing runs.
- A `FAILED` event is retried in place until it 200s.
- Two simultaneous deliveries of one event → unique key on
  `payment_events.razorpay_event_id`; the second is told "already being handled".
- Non-200s happen only on real problems, deliberately, so Razorpay retries.

### The status matrix (what fires when)

| Razorpay event | What we do |
|---|---|
| `subscription.activated` / `resumed` | → `ACTIVE` + real period dates (`current_start`/`current_end`) + plan/cycle sync + `payment_method` snapshot + **kill the live trial** |
| `subscription.charged` | Same as above **plus** a payment snapshot |
| `subscription.updated` | Plan/cycle re-sync only (a plan change happened) |
| `subscription.pending` | → `PAST_DUE` (renewal is retrying — grace period) |
| `subscription.halted` | → `HALTED` (autopay retries exhausted; access gone until resume) |
| `subscription.cancelled` | → `CANCELLED` (also: the abandoned-checkout clearer from §4) |
| `subscription.completed` / `expiry` | → `EXPIRED` |
| `payment.authorized/captured/failed` | **No decision** — snapshot money, nothing else. The subscription lifecycle decides access. |
| anything else | recorded `IGNORED`, 200. |

Deliberate quirk: **one bounced payment does not demote the plan.** The row stays
`ACTIVE` until Razorpay's lifecycle event says `pending`/`halted`. We mirror
Razorpay's state machine instead of reacting to every payment blip.

---

## 6. When a webhook never arrives — the pull side

The webhook is push. Push can fail (our URL unreachable during an outage,
signature changed, Razorpay gave up retrying after ~24h). When that happens,
the payment succeeded but our row still says `PENDING` — until **somebody
pulls**. `SubscriptionSyncService.syncFromRazorpay` is that somebody: it fetches
`GET /v1/subscriptions/{id}` and applies **exactly the same transition the
webhook would** — so a payment that succeeded but whose push never landed gets
turned into access. Idempotent — if the late webhook lands afterwards, it's a no-op.

Three triggers share this one code path:

**(a) Self-healing poll — `GET /api/subscription/{subscriptionId}` (customer)**
The UI's checkout loop; the row is read org-scoped (foreign id → 404) and while
the row is still `PENDING`, **each poll also asks Razorpay**. If Razorpay says
paid-and-active, the row becomes `ACTIVE` client-invisible-fast: the very next
poll reports `active=true` and the UI redirects. Nobody paid is ever stuck.
Razorpay unreachable → fail-soft: poll still returns 200 with the local snapshot;
the loop just tries again 2–3 s later.

**(b) Admin console sync — `POST /internal/api/subscriptions/{id}/sync`**
The support tool for a customer whose browser closed right after paying (nobody
is polling, webhook was lost). Support hits sync, the row reconciles against
Razorpay, and the refreshed row comes straight back ("what it is now").
Razorpay down here is fail-**loud** — support *should* see it, since Razorpay
being down is the same pipeline that swallowed the webhook in the first place.

**(c) `cancel-checkout` guard — the bare minimum inside the release endpoint**
Before releasing a stuck `PENDING` row, fetch Razorpay's state:

| Razorpay says | We do |
|---|---|
| `created` / `authenticated` (checkout truly dead) | `DELETE` the Razorpay subscription + row → `CANCELLED` (normal case) |
| `active` / `activated` / `resumed` (they actually paid!) | **never cancel anything** — run the activation sync instead; a lost webhook becomes access, not a refund dispute |
| anything in-between (`pending`/`halted`/`cancelled`/`expired`) | Mirror that state locally, no delete |
| Razorpay unreachable | Cancel locally only, never DELETE unseen |

Without this guard, the original code would DELETE a Razorpay subscription that
just got paid — customer charged, no access, no refund tooling. With it,
"cancel stuck checkout" is safe in every case.

### 6.1 What the frontend should do (the checkout playbook)

This is the frontend's whole contract. One loop, one key, five exits.

**The loop:**

1. User picks a plan → `POST /api/subscription/subscribe`.
2. From the response keep **`subscriptionId`** (this is *your* local key — not the
   Razorpay id) and open Razorpay Checkout using `razorpaySubscriptionId` +
   `razorpayKeyId`.
3. Start polling: **`GET /api/subscription/{subscriptionId}` every 2–3 s**.
   Keep the timeout generous (~10 min); the endpoint does all the healing work
   server-side, the frontend just waits.
4. Every response is the same body shape as `GET /api/subscription`, so one
   parser/handler is reused everywhere. Branch on two booleans:

| Response | Meaning | Do |
|---|---|---|
| `active=true`, `status=ACTIVE` | payment landed (webhook or self-heal) — success | **stop polling, redirect to the app home**; refresh quota/plan data (trial is superseded by now) |
| `status=PENDING`, `active=false` | checkout still unsettled | keep polling; show a mild "processing…" state |
| `status=CANCELLED` / `EXPIRED`, `active=false` | checkout never completed **or** released | **stop polling**, clear the loop, offer "try again" (`subscribe` is free to call — the blocker is gone) |
| `status=NONE` | shouldn't happen for the polled row | treat like CANCELLED: drop the view, surface support |
| HTTP 404 | id isn't this org's row (bug or stale page) | stop polling, do **not** retry; log + generic error |
| HTTP 5xx | backend hiccup (transient) | retry the *same* tick per your backoff; after ~3 consecutive 5xx, show a retry button |

**Timers / caps:**

- Poll every 2–3 s, budget ~10 min total. Beyond that, stop polling and show
  "Still processing? If your payment went through, this page heals itself —
  refresh in a minute, or contact support."
- Do **not** stop early because PENDING took "too long": Razorpay can be slow
  (₹ mandates, bank OTP retries). `PENDING` is genuine work-in-progress, not an
  error.
- Don't poll faster than 2 s — you get nothing extra (the server already drives
  the pull cadence per poll) and just spend Razorpay calls.

**Rules of thumb (what NOT to do):**

- Don't auto-fire `POST /api/subscription/cancel-checkout` from the polling UI
  on any detected slow status — automation on that endpoint has no
  user-visible feedback. A **user-initiated** "Cancel this checkout" button
  (see Backstop messaging below) or support doing it is the safest pattern.
- Never "optimise" by inferring success from the Checkout JS callback alone
  (`checkout.js`'s success event only means *see you later, Razorpay* — the
  settlement truth is the polling response). Close only on `active=true`.
- The Razorpay id is *not* the subscription id — the loop is keyed by our local
  `subscriptionId` only.
- If the user navigates away mid-poll, stop the timer; on return, resume from
  `GET /api/subscription` (not the keyed endpoint, which is the same read,
  but state-machine-free).
- After the redirect on `active=true`, refresh anything plan-dependent (bars,
  feature flags) from `GET /api/me` / `GET /api/plans` — don't trust pre-poll data.

**Backstop messaging (when the loop times out):**

- If the user still sees `PENDING` after the budget: tell them the payment will
  finish either way (the backend reconciles for up to 3 more minutes via the
  abandoned-checkout self-expire), and if the user believes they paid, direct
  them to support — support resolves it in one console action (§10).
- If the user is sure they never confirmed the payment: offer
  `POST /api/subscription/cancel-checkout`. A `409 no_pending_checkout` response
  here is *good news* — something already resolved it server-side; refetch once
  to show the true state before doing anything else.

### Razorpay statuses we deliberately don't act on

`created` and `authenticated` mean "checkout not settled yet" — the row stays
`PENDING` and the next poll/sync re-checks. `ACTIVE` is never fabricated without
Razorpay's period dates (`current_start` / `current_end`).

---

## 7. Trial expiry — `SubscriptionExpiryJob`

- Nightly at 00:05 UTC (`app.jobs.trial-expiry.enabled`, `app.jobs.trial-expiry.cron`).
- Job only handles `TRIALING` rows with `endDate < today` → `EXPIRED`.
  **Paid rows are never touched here** — Razorpay's event stream owns those; a
  local-only trial has no Razorpay stream, which is why this job exists at all.
- `EXPIRED` is a clean terminal state (UI shows "expired, renew"). Buying a plan
  afterwards works — `EXPIRED` isn't a blocker. This used to be a known gap: a
  lapsed trial would stay "live" forever.

---

## 8. Plan change — `POST /api/subscription/change-plan`

Owner-only. Requires an `ACTIVE` row (never `PAST_DUE` — Razorpay refuses PATCH
in `pending`). `payment_method=upi` → 409 `plan_change_unsupported_upi` (UPI
mandates are fixed). Same plan+cycle → 409 `plan_change_same_plan`.

1. Validate (above), lock the org row.
2. `PATCH /v1/subscriptions/{id} {plan_id, schedule_change_at:"now"}` — Razorpay
   prorates (upgrade = differential charge, downgrade = refund). No math here.
3. Our local row's plan/cycle flip **later**, via the `subscription.updated`
   webhook (or the next `charged` event's plan_id). Local state goes stale for a
   few seconds on purpose — Razorpay's answer is trustworthy and its retries work.

---

## 9. Plan capability checks — `PlanLimitService`

Plan columns (all nullable; `null` = unlimited):

| Column | Meaning | Who asks |
|---|---|---|
| `max_users` | hard seat cap; seats are employee rows (owner is not a seat) | invites (send + re-check at accept), employee create |
| `max_cars` | soft — warn, never blocks | UI |
| `report_window_months` | report history window | report consumers |
| `stats_range_years` | statistics range; `0` = page hidden | charts + KPI tiles |
| `invoice_generation` | boolean | invoice consumers |
| `is_trial`, `is_public`, `sort_order` | the one Trial row; pricing visibility/order | trial start, `GET /api/plans` |

One resolution path everywhere (`SubscriptionReadService.liveForOrg`) so no gate
disagrees. Missing live plan on a gate → `409 org_no_live_subscription`
(invites / employee create) or `409 statistics_not_available` (statistics) with
`stats_range_exceeded` for the range case.

---

## 10. The support runbook (when things get stuck)

| Symptom | What support does |
|---|---|
| Customer teaches "checkout stuck processing" for >10 min | Ask them to keep polling; if it self-heals, done. |
| Still stuck, browser still open | sync from console (§6b) → row goes ACTIVE → they refresh and are in. |
| Customer closed the browser after paying | diff via console sync → write back; customer just refreshes. |
| Very old row (days), nobody polled anywhere | console sync still works; if Razorpay also says created (checkout never finished) the row was just a reservation — cancel it (§6c) and ask the customer to try again. |
| Razorpay itself responds error on console sync | Razorpay is down; wait, then retry. Logs / `payment_events` show which webhook stream went silent. |

---

## 11. Known limits (honest list)

- No refunds or dunning UI — Razorpay retries its own autopay chain; fallout beyond that is manual.
- Plan change depends on Razorpay's `subscription.updated` to land; if it doesn't
  (same lost-webhook catch), the local row keeps the old plan/cycle until the next
  `charged` re-sync or a console sync.
- The nightly job heals lapsed trials, not stale PENDINGs — the console sync is
  the manual path for those (auto job deliberately not added yet).
- Test clock: the trial end date is UTC; the job checks UTC midnight — the UI can
  briefly show `daysRemaining=0` with `active=true` before 00:05 UTC.

## 12. What changed in this file recently

- Trial expiry job (§7) instead of "not built yet".
- Subscribe's three-step ordering + (a)/(b)/(c) reconcile paths (§6) with
  `expire_by` and `cancel-checkout` made reconcile-first (§6c).
- `PlanLimitService` consolidation (§9).
- §6.1: the frontend's polling contract written out (loop, exits table, timers,
  don'ts, backstop messaging) — self-healing poll included.
