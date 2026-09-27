# Plan catalog, org creation & usage quotas

For the money/webhook/checkout lifecycle of an active subscription, see
`docs/subscription-flow.md` and the design doc
`razorpay-saas-billing-entitlements.md`. This doc covers the adjacent pieces:
the read-only plan catalog, how a user's org is born, and where quota/feature
usage is surfaced.

## What it does

**`GET /api/plans`** — read-only, behind auth, not org-scoped
(`PlanController` → `PlanService.listPublic()`). Returns `is_public` plans
ordered by `sort_order`. Prices are **never stored locally** — each plan has
`razorpay_monthly_plan_id` / `razorpay_yearly_plan_id` (nullable; a cycle the
plan doesn't sell is absent from the response) and amounts are fetched live
from Razorpay per request. Razorpay unreachable → the whole endpoint 500s
(fail-closed, no stale prices). Also carries `limits` (`maxUsers`, `maxCars`)
and `features` (`reportWindowMonths`, `statsRangeYears`, `statisticsPage`,
`invoiceGeneration`) — `null` means unlimited.

**The five plans** (Trial, Starter, Workshop, Pro, Enterprise) are rows in
`subscription_plans`, inserted manually by the operator (no seed data).
Trial is a real plan row (`is_trial = true`, exactly one), not a per-plan
modifier — it has small `max_users`/`max_cars` but every *feature* unlocked,
so it previews the product at reduced limits rather than reduced features.

**Users ⇄ orgs is 1:1.** A user is created org-less; the org is born the
first time they start a trial or buy a plan (org details come in that request
body — there is no standalone `POST /api/orgs`). `users.org_id` is the only
link; the owner is simply the member with `role = owner`.

**`POST /api/subscription/trial`** creates the org, links the caller as
`owner`, opens a `trialing` subscription against the (single) Trial plan, and
returns a fresh access token carrying the new `org_id`. One trial per account,
ever (`users.trial_used`), enforced with a `PESSIMISTIC_WRITE` lock on the
user row to serialise concurrent attempts.

**`GET /api/subscription`** is always 200, returning `active`/`onTrial`
booleans, status, and a full embedded `plan` block (same shape as
`GET /api/plans` minus pricing).

**Plan usage on `GET /api/me`** (`UserProfile.plan`, a `PlanUsage`) gives a
slim max-vs-current view for quota bars: `maxUsers`/`currentUsers`,
`maxCars`/`currentCars` (`current*` is a live `COUNT(*)`, not cached), plus
`reportWindowMonths`/`statsRangeYears` read straight off the plan. `null` for
an org-less caller or one whose org has no live subscription.

**Enforcement** — one injectable `PlanLimitService` answers
`assertCanAddUser(orgId)` (seat cap; employee rows are the seats) and
`assertStatsRangeAllowed(orgId, from)` (used by the charts endpoints). Hard
breaches throw a coded `ConflictException` (409) so the UI can show an
upgrade CTA.

## Files that implement this

| File | Role |
|---|---|
| `entity/SubscriptionPlan.java` | The catalog row: pricing plan ids, capability columns (`maxUsers`, `maxCars`, `reportWindowMonths`, `statsRangeYears`, `invoiceGeneration`, `isPublic`, `sortOrder`, `isTrial`). |
| `repository/SubscriptionPlanRepository.java` | `findByIsTrialTrue()` and public/sorted lookups. |
| `controller/PlanController.java` | `GET /api/plans`. |
| `service/PlanService.java` | Builds `PlanResponse`, fetches live Razorpay pricing per offered cycle. |
| `dto/PlanResponse.java` | Catalog response shape (`pricing`, `limits`, `features`). |
| `controller/SubscriptionController.java` | `POST /api/subscription/trial`, `GET /api/subscription`, `GET /api/subscription/{id}`. |
| `service/SubscriptionService.java` | `startTrial` (org creation, owner linking, trial subscription, `TRIAL_DAYS` = 14), reads `getCurrentForOrg`. |
| `service/internal/SubscriptionReadService.java` | Shared "resolve org → live subscription → plan" lookup used by `PlanLimitService` and `UserService`. |
| `dto/CurrentSubscriptionResponse.java` | `GET /api/subscription` response shape. |
| `dto/UserProfile.java` | `plan: PlanUsage` block on `GET /api/me`. |
| `service/UserService.java` | Builds `PlanUsage` (delegates the "has a live subscription" check to `SubscriptionService`), live `COUNT(*)` via `UserRepository`/`CarRepository`. |
| `service/PlanLimitService` (package `service/internal`) | `assertCanAddUser`, `assertStatsRangeAllowed` — the single enforcement chokepoint. |
| `entity/User.java` | `orgId` (nullable), `role`, `trialUsed` — the 1:1 user⇄org link. |
| `db/changelog/migrations/` | `subscription_plans` schema and capability columns (see `001-initial-schema.sql` + later plan-column migrations). |
