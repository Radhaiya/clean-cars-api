# CLAUDE.md

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working style — ask, don't assume

When a request leaves **any** decision open — endpoint shape, auth/role rules, error codes, which of several plausible behaviours, schema columns, naming — **ask before building**. Do not pick a default and proceed silently. Reading the code first to ground the questions is expected; guessing the user's intent is not. Prefer a short round of clarifying questions over a wrong implementation.

## Stack

Spring Boot 4.1.1 · Java 25 · Gradle 9.7.1 (wrapper) · Spring Data JPA / Hibernate · MySQL 8 · stateless JWT auth. Lombok is used throughout.

## Commands

```bash
# Database — MySQL on host port 3370 (root/root, db `cleancars`).
# Starts EMPTY — the `liquibase` compose service applies db/changelog/migrations/*.sql
# on `docker compose up -d` (run-once container; idempotent). The app re-checks on boot.
docker compose up -d
docker compose run --rm liquibase   # apply pending migrations on demand (after adding a file)
# docker compose down -v           # FORBIDDEN for the agent (wipes the MySQL volume —
#                                   # irreversible data loss, see STRICT section below);
#                                   # developer-only, on explicit instruction

# Full stack in Docker (own MySQL volume — stop the root stack first; both bind :3370):
# MySQL + Liquibase + the API built from local source, listening on :8089.
# Backend reads scripts/.dev.env (Razorpay test keys); DB URL is repointed at the
# docker-network mysql service via SPRING_DATASOURCE_* env overrides.
docker compose -f backend-service-docker-compose.yml up -d --build
docker compose -f backend-service-docker-compose.yml down
# After adding a migration with the stack already up: `up -d liquibase` re-runs the
# run-once migration container (bind mount sees the new file — no rebuild); use the
# full `up -d --build` when code changes ship with the migration.

# Run the app — listens on :8089 (server.port in application.yml). Needs the DB up.
./gradlew bootRun

# Build / test (each @SpringBootTest boots the full context against in-memory H2 — no Docker needed)
./gradlew build
./gradlew test
./gradlew test --tests 'com.example.cleancarsapi.CleanCarsApiApplicationTests'
./gradlew compileJava          # fast compile-only check

# Get a token for manual API calls — login is Firebase-only (Google/Apple/OTP), no password:
#   POST /api/auth/firebase  {"idToken":"<firebase id token>"}  -> {token, refreshToken}
#   then send  Authorization: Bearer <token>
# Getting a real Firebase ID token locally means signing in through the Firebase JS SDK
# (project: mygarageone-test) and reading the token off the client — see "Auth / tokens" below.
```

There is no linter configured.

**Sandbox / network:** in this environment the default Bash sandbox blocks outbound network, so `./gradlew` cannot download the Gradle distribution or dependencies. Run all Gradle commands with the sandbox disabled.

## Canonical convention doc

`docs/ARCHITECTURE.md` is the source of truth for how code is organised here. Read it before adding a resource. The load-bearing rules:

### Package-by-layer
`controller / service / repository / entity / dto / security / config / exception` — one folder per layer, not per feature.

### Four services per CRUD resource
Every resource exposing full CRUD gets **four separate `@Service` classes**: `X{Create,Read,Update,Delete}Service`. `Read` owns both single-get and list. Never a single fat `XService`. The controller injects all four and delegates one line per endpoint. Field-copy logic shared by create/update lives on the request DTO as `applyTo(entity)`. Read-only lookups that are *not* CRUD resources (`UserService`, `OrganizationService`, `BrandModelService`) stay single classes. Reference impl: `customer` and `car-brand` / `car-model` / `car`.

### Multi-tenancy
Every business row carries `orgId`, set once on create from the token (`@Column(updatable = false)`), never taken from the request body/path/query. Always resolve it via `AuthContext.requireOrgId()`. Repository finders are org-scoped (`findByIdAndOrgId`, `existsByOrgIdAnd...`). A lookup that misses returns `NotFoundException` (404) — a caller must not be able to distinguish "wrong org" from "does not exist". `CarReferenceValidator` shows how create/update check that referenced ids (customer/brand/model) belong to the caller's org.

### AuthContext
`AuthContext.require()` / `requireOrgId()` / `require(UserRole)` is a static accessor for the current `AuthenticatedUser` (userId, orgId, role, email, name), usable in any layer. It is populated by `AuthenticatedUserJwtConverter` from the JWT `sub` / `org_id` / `role` claims — no extra DB hit, no parameter threading.

### Auth / tokens
`/api/auth/**` is permit-all; everything else needs a Bearer access token.
- **Login is Firebase-only** — `POST /api/auth/firebase` `{idToken}`. Firebase Auth is the single front door for every sign-in provider (Google, Apple, phone OTP) — whichever the client used, it always hands back a Firebase ID token, so this is the only login endpoint and the only identity verifier the backend needs. There is no password: `users.password_hash` never comes back. `FirebaseIdTokenService` verifies the token against Firebase's JWKS (`https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`), its issuer (`https://securetoken.google.com/<app.firebase.project-id>`) + audience + expiry, and (when an email claim is present at all) `email_verified`. `app.firebase.project-id` / env `FIREBASE_PROJECT_ID` — Firebase project is `mygarageone-test`.
- **Identity key is `users.firebase_uid`**, not email — `email` and `phone` are nullable contact info (phone-only OTP sign-ins have no email; most Google/Apple sign-ins have no phone). Resolution order in `AuthService.loginWithFirebase`: existing `firebase_uid` → existing row by `email` (one-time bridge, backfills `firebase_uid` onto it) → provision a brand-new org-less user (`User.provisionFromFirebase`, `role = staff`, `status = active` — Firebase already verified the identity, so there's no separate invited/activation step) — then goes through the normal onboarding-gate → start-trial flow.
- **Access token**: HS256 JWT signed with `app.jwt.secret`, lifetime `app.jwt.ttl-seconds`. Verified by the resource server, turned into the `AuthenticatedUser` principal.
- **Refresh token**: opaque random string, only its SHA-256 hash stored (`refresh_tokens` table), lifetime `app.jwt.refresh-ttl-days`. `POST /api/auth/refresh` rotates it (old one revoked). Replaying a revoked refresh token revokes **all** of that user's tokens — `TokenReuseException` + `@Transactional(noRollbackFor = ...)`, and `AuthService` deliberately holds no transaction of its own so the lockout can't be rolled back.

### Errors
Throw a domain exception; `ApiExceptionHandler` (`@RestControllerAdvice`) maps it to an RFC-7807 `ProblemDetail`: `NotFoundException` → 404, `ForbiddenException`/`DisabledException` → 403, `ConflictException` → 409 (carries a machine-readable `code`, e.g. `customer_phone_exists`), `MethodArgumentNotValidException` → 400 with an `errors` map, `DataIntegrityViolationException` → 409 (race safety net). Uniqueness rules are pre-checked in the service for a clean coded 409 **and** backed by a DB unique constraint.

### Lombok
Entities: `@Getter`, `@Setter` only where mutated, `@NoArgsConstructor` for Hibernate — never `@Data`/`@ToString`/`@EqualsAndHashCode` on an `@Entity`. Services & controllers: `@RequiredArgsConstructor` over `private final` fields, `@Slf4j` where logging is needed. Not applied to `SecurityConfig` / `JwtService` (their constructors build the HMAC key from `@Value`s). DTOs are `record`s.

### Enums <-> DB
MySQL `ENUM` columns store lowercase; Java enum constants stay UPPERCASE. Bridge with a JPA `AttributeConverter` marked `@Converter(autoApply = true)` (`UserRoleConverter`, `FuelTypeConverter`). Inbound JSON enum parsing is case-insensitive (`spring.jackson.mapper.accept-case-insensitive-enums` in `application.yml`).

### Pagination
List endpoints return `PageResponse<T>` (a stable envelope) — never a raw Spring Data `Page`. `spring.data.web.pageable.max-page-size: 50` in `application.yml` caps every endpoint's `?size=` globally (Spring silently clamps a larger request down to 50, no error) — per-endpoint `@PageableDefault(size = ...)` only sets that endpoint's *default*, not its ceiling.

### CORS
`SecurityConfig` wires `.cors(Customizer.withDefaults())`, backed by a `CorsConfigurationSource` bean built from `CorsProperties` (`@ConfigurationProperties(prefix = "app.cors")`, origin **patterns** not exact strings). Method/header/credentials/max-age are shared in `application.yml`; only `allowed-origin-patterns` varies per profile — `local` allows any `http://localhost:*` / `http://127.0.0.1:*`, stage/prod read `CORS_ALLOWED_ORIGINS` (optional; unset = no cross-origin browser calls allowed, same fail-closed default `FIREBASE_PROJECT_ID`/`JWT_SECRET` use for the other stage/prod-only settings). No real stage/prod frontend origin is configured yet.

## Schema is managed by Liquibase, not Hibernate

`spring.jpa.hibernate.ddl-auto: none`. The schema's source of truth is the Liquibase changelog: `db/changelog/db.changelog-master.yaml` auto-includes every SQL-formatted file under `db/changelog/migrations/` in alphabetical order (`001-initial-schema.sql` is the full baseline — every PK/FK is a `BINARY(16)` UUID generated app-side by Hibernate's `@UuidGenerator`). Liquibase runs on every boot in every profile (local, stage, prod, test), applied once per database via `DATABASECHANGELOG`. This needs `spring-boot-starter-liquibase` in `build.gradle` — on Spring Boot 4 plain `liquibase-core` does **not** auto-configure it. Each boot prints the migrations it ran (`Running Changeset: db/changelog/migrations/NNN-….sql` lines + an `UPDATE SUMMARY` block; `show-summary` in `application.yml`, `liquibase.ui` logger enabled in `application-prod.yml`). **Changeset identity includes the file path:** the compose `liquibase` container is mounted so it records the same `db/changelog/migrations/...` filenames as the app — keep them aligned (a mismatch makes the app re-run applied migrations and crash on the first non-idempotent one). `src/main/resources/cleancars_schema (1).dbml` is the design doc — keep it in sync with the changelog.

**Changing the schema means dropping a new `002-*.sql` (etc.) file into `db/changelog/migrations/` — never edit an already-applied file** (checksums). Hibernate entities must mirror the columns; the app owns id generation (`@UuidGenerator`), the DB owns timestamps.

### STRICT: Never destroy the database volume or its data

**`docker compose down -v` (volume wipe) and any command that erases the MySQL volume/dataset is FORBIDDEN** unless the developer explicitly asks for it in their own words in the current conversation. "Resetting", "rebuilding", or "fixing" Liquibase state is not such an ask — no destructive shortcut, ever. This is irreversible data loss: operator-inserted rows (`subscription_plans`, Razorpay plan ids), test-built data, and any locally captured data are gone and cannot be recovered.

**If a changelog file is "polluted"** — edited after being applied (checksum mismatch, or a changeset was renamed/renumbered and now disagrees with `DATABASECHANGELOG`) — **do nothing destructive yourself**. Do not wipe the volume, do not delete rows from `DATABASECHANGELOG`, do not run DML. Instead: stop and tell the developer which file is polluted and what happened, and let **them** delete/fix that changelog file manually (or run `docker compose down -v` themselves if they choose to). The agent's job ends at reporting the problem clearly.

Operator-inserted data (the `subscription_plans` rows, for example) is inside the volume — losing it costs real work. Treat every `down -v`, `docker volume rm`, or schema-wipe as a fire drill: never proactive, only on an explicit, unambiguous developer instruction.

**DDL only via changelogs against the local MySQL.** Running manual schema/table statements — `CREATE` / `ALTER` / `DROP` / `ADD` / `MODIFY` / `RENAME` — is not needed anymore; edit the changelog and restart. Do **not** run DML: no `INSERT`, `UPDATE`, or `DELETE` of data. `docker compose down -v` is destructive data loss (see the STRICT section above) — only the developer runs it, and only when they explicitly choose to.

**No seed data.** The DB starts empty — plans, orgs, and users are all created through the app (`subscription_plans` rows are inserted manually by the operator, e.g. the one Trial row every account needs before `POST /api/subscription/trial` can work).

Timestamps: `@CreationTimestamp` / `@UpdateTimestamp` for app-managed rows; `@Column(insertable = false, updatable = false)` where the DB owns `created_at`/`updated_at`.

## Timestamps & timezones — UTC in, org timezone out

The JVM and MySQL both run in UTC; every `LocalDateTime` in the app means UTC
wall time. Each org carries an IANA timezone id (`organizations.timezone`),
and responses convert UTC → the org's wall time only at the JSON boundary.
**When working on timestamps, timezones, or the `GET /api/reference`
timezone pick-list, read `docs/FEATURE-TIMEZONES.md`** — it holds the
implementing files (`TimezoneJacksonConfig`, `OrgTimeZoneResolver`,
`ReferenceDataService`) and the UTC-day vs org-day caveats.

## Organization currency & tax name

`organizations.currency_code`/`currency_symbol` and `organizations.tax_name`
are two independent, display-only org settings (currency is compulsory at
trial creation; tax name is an optional label, not a rate — the rate always
stays per line item via `TaxBreakdown`). **When working on either, read
`docs/FEATURE-ORG-CURRENCY-TAX.md`** — it holds the implementing files
(`ReferenceDataService`, `Organization`, the two migrations) and the exact
validation/clearing rules for `PUT /api/organization`.

## Config profiles

`application.yml` (common) + `application-{local,stage,prod}.yml` (main resources) + `application-test.yml` (test resources, activated by `@ActiveProfiles("test")`). There is **no default profile** — `SPRING_PROFILES_ACTIVE` is required and startup fails without it (`CleanCarsApiApplication.main`); for local dev put `SPRING_PROFILES_ACTIVE=local` in `scripts/.dev.env` (bootRun loads it) or in the IDE run config. `stage`/`prod` read `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` from the environment. `local` points at the Docker MySQL on `localhost:3370`. `test` uses in-memory H2 (MySQL mode): Liquibase is disabled (the migrations are MySQL-only), Hibernate builds the schema (`create-drop`), and `src/test/resources/data.sql` adds the column defaults and seed rows the migrations normally provide — when a new NOT NULL DEFAULT column breaks a raw-SQL test insert, add its default there.

## Subscription plans & billing

**Payments provider: Razorpay** (recurring subscriptions), wired for: pricing read side, checkout, and webhook sync. Razorpay is the **source of truth for money** — price, currency, billing interval, subscription charging, retries/dunning. The entitlements/limits stay in our DB (`subscription_plans` capability columns). `razorpay-saas-billing-entitlements.md` is the design doc the implementation follows.

- **Prices are never stored locally.** `subscription_plans.monthly_price` / `yearly_price` are **gone**; a Razorpay Plan is per-cycle, so each plan has `razorpay_monthly_plan_id` / `razorpay_yearly_plan_id` (nullable — a cycle the plan doesn't sell is NULL; the Trial row has both NULL). Amounts are fetched **live** from Razorpay by `PlanService` (`RazorpayGateway.fetchPlan`); Razorpay being unreachable fails `GET /api/plans` (500, fail-closed — no stale prices). The 8 real Razorpay plan IDs must be created in the Dashboard and filled into the live `subscription_plans` rows (`TODO(razorpay)`).
- **Config:** `app.razorpay.key-id` / `key-secret` / `webhook-secret` (`RazorpayProperties`, env `RAZORPAY_*`; stage/prod fail startup when unset). Test-mode keys locally.
- **Checkout:** `POST /api/subscription/subscribe` `{razorpayPlanId}` — the **Razorpay Plan ID** the client picked from `GET /api/plans` pricing (`pricing.monthly.razorpayPlanId` / `pricing.yearly.razorpayPlanId`); it encodes both the plan and the billing cycle, so `SubscriptionService.subscribe` resolves the internal row by matching whichever `razorpay_monthly_plan_id` / `razorpay_yearly_plan_id` column holds it (unknown id → 404; hiding a plan with `is_public = false` also 404s). Ordering is money-safe: an org-row `PESSIMISTIC_WRITE` serialises it, the local `PENDING` row is committed **before** the Razorpay create call (one `@Transactional` can't wrap that — `TransactionTemplate` runs the two short transactions), the Razorpay call carries `expire_by = now + 30 min` (abandoned checkout self-cancels on Razorpay → `subscription.cancelled` webhook clears the row), and a failed Razorpay call flips the row `CANCELLED` in its own tx so the org can retry immediately. Returns the Razorpay subscription id + our key id for `checkout.js`. Subscribing while already ACTIVE/PAST_DUE/PENDING → `409 org_already_subscribed`; converting a live trial is **allowed** — when Razorpay activates the paid sub the webhook supersedes (`CANCELLED`) the trialing row (trial ends early; it was free).
- **Polling callback:** `GET /api/subscription/{subscriptionId}` — the UI's checkout loop (poll every 2–3 s after Checkout opens, keyed by the local `subscriptionId` returned from `/subscribe`); same body shape as `GET /api/subscription`; org-scoped `findById` (foreign id → 404); **self-healing** — while the polled row is still `PENDING` with a Razorpay id, the endpoint itself reconciles against Razorpay (`SubscriptionSyncService.syncFromRazorpay`, fail-soft — a lost activation webhook heals on the next poll; `created`/`authenticated` Razorpay states leave it PENDING); redirect when `active=true`. Reconciliation fallbacks for checkouts nobody polls: owner-only `POST /api/subscription/cancel-checkout` is **reconcile-first** (Razorpay says paid/full-dead/unreachable → sync/mirror/local-cancel — it never DELETEs a paid Razorpay subscription; dead `created` checkout only), and the admin console has `POST /internal/api/subscriptions/{id}/sync` (fail-loud, returns the refreshed row) — Razorpay being down throws there so support sees it.
- **Webhooks:** `POST /api/webhooks/razorpay` — permit-all (`SecurityConfig`), authenticated instead by the `X-Razorpay-Signature` HMAC (`app.razorpay.webhook-secret`) checked before anything else; invalid → 401. Processing (`RazorpayWebhookService`, `@Transactional(noRollbackFor = RazorpayWebhookException.class)` — same pattern as the token-reuse lockout so the FAILED event row survives the rethrow): dedupe on `payment_events.razorpay_event_id` UNIQUE (PROCESSED/IGNORED duplicates → 200 no-op; FAILED rows are updated and re-run on Razorpay's retry), raw payload appended to `payment_events` idempotently, then the status matrix: `activated/charged/resumed` → `active` (+ period dates from `current_start`/`current_end`, + trial supersede), `pending` → `past_due` (grace), `halted` → `suspended`, `cancelled` → `cancelled`, `completed/expiry` → `expired`; `payment.authorized/captured/failed` only snapshot into `razorpay_payments` (keyed by `razorpay_payment_id` UNIQUE, upserted — amount in paise, currency, status, paid_at) and make **no access decision by themselves** — the subscription lifecycle event does. A webhook referencing an unknown in-flight subscription → recorded FAILED + rethrown → non-200 → Razorpay retries. Payment events make no access decision — the subscription lifecycle event does.
- **Plan change (up/downgrade):** `POST /api/subscription/change-plan` `{razorpayPlanId}` on the org's ACTIVE subscription → `PATCH /v1/subscriptions/{id}` `{plan_id, schedule_change_at: "now"}`. Razorpay does all proration: upgrades charge only the remaining amount (differential invoice on the saved autopay), downgrades refund the difference — no new checkout, no money math in our code. The local row's `plan_id`/`billing_cycle` flip on the `subscription.updated` webhook (`RazorpayWebhookService.applyPlanMapping`; `subscription.charged` also re-syncs the mapping from the payload's `plan_id`, covering cycle-end scheduled changes). Requires ACTIVE (not PAST_DUE — Razorpay rejects plan changes in `pending`); same plan+cycle → `409 plan_change_same_plan`.
- **Status:** `subscriptions.status` is one DB ENUM extended to `trialing|pending|active|past_due|suspended|cancelled|expired` (+`SubscriptionStatus` enum / converter); there is no separate razorpay_status vs access_status yet — one column maps Razorpay state → access semantics, split later only if a real need appears. Unknown Razorpay plan subscription chain (no `cancel_at_period_end` flag stored yet).
- **Period dates are Razorpay's**, from the webhook payload (`current_start`/`current_end` epoch seconds) — not derived from our clock.
- **Not yet built:** Razorpay SDK-independent (plain `RestClient`, no `razorpay-java`); refunds, proration, invoice sync, `subscription.cancelled` initiated by us (cancel-at-period-end policy), `payment_link`/`payment.page` variants, retry/dunning UI states beyond the status matrix, and the Dashboard-side webhook registration (the `X-Razorpay-Event-Id` header — falls back to a SHA-256 body hash when absent).

### Plan catalog, org creation & usage quotas

Covers `GET /api/plans` (the read-only catalog, live Razorpay pricing), the
five plan rows (Trial, Starter, Workshop, Pro, Enterprise) and their
capability columns, the 1-user:1-org model, `POST /api/subscription/trial`,
`GET /api/subscription`, and the `plan` usage block on `GET /api/me`.
**When working on any of these, read `docs/FEATURE-PLANS-CATALOG.md`** — it
holds the implementing files and the exact gating/quota rules.

### Charts, KPI tiles, org totals & dashboard

Covers `GET /api/charts` (bucketed `TOTAL_SERVICE`/`TOTAL_REVENUE`, gated by
`stats_range_years`), `GET /api/charts/kpi-tiles` (flat P&L total), `GET
/api/charts/totals` (all-time counts, not gated), and `GET /api/dashboard`
(the always-available home-page snapshot). **When working on any of these,
read `docs/FEATURE-CHARTS-DASHBOARD.md`** — it holds the implementing files
and the exact gating/date-boundary rules.

### Expenses — `GET/POST/PUT/DELETE /api/expenses`, `/api/expense-categories`

Full CRUD resource pair: `ExpenseCategory` is a per-org label dropdown, and
`Expense` is one row per entry, feeding `totalExpenses`/`totalProfit` on
`GET /api/charts/kpi-tiles`. **When working on either resource, read
`docs/FEATURE-EXPENSES.md`** — it holds the implementing files and the
denormalized-category-string rule (not an FK).

### Split payments — one-time vs split on service orders

Each service order has a `paymentPlan` (`ONE_TIME` | `SPLIT`); both write to one `payments` ledger, `paid`/`amountPaid` are derived, and charts/dashboard count money actually received. **When working on payments, `paid`, or revenue/unpaid math, read `docs/FEATURE-SPLIT-PAYMENTS.md`** — it holds the rules, error codes, migration notes and file map.

### AMC (maintenance contracts) + line discounts — `docs/FEATURE-AMC.md` is the source of truth

AMC plans (fixed service bundle) → variants (tenure + frequency + per-service quantity / price / tax, **no flat price**) → sold to a **car or bike**
(snapshot + one upfront payment, separate revenue stream) → redeemed once per period as a service order whose lines are the bundle at a
**hard-set 100% discount** (server-built, locked, ₹0, never a bill). Used / lapsed / remaining are **computed at runtime** (`AmcSlots`), never
stored. Also introduces the per-line, per-unit **`discount_amount`** on every service order (`TaxBreakdown.ofLine`; zero-total orders count as `paid`).
**When working on AMC, plans/variants, sales, redemption, slots, `amc_*` tables, order discounts or `amc_enabled`, read `docs/FEATURE-AMC.md`** — it holds
the rules, migrations 014–018, the API contract, error codes and file map. AMC revenue reporting is intentionally not built yet.

### Feature properties — runtime switches

`feature_properties` (key → JSON value) drives maintenance mode (`Client.Maintenance.Mode.Enable`) and the new-user login gate (`Client.New.Logins.Disabled`); the console edits it, `GET /api/properties` serves the `Client.*` keys publicly. **When working on these, read `docs/FEATURE-PROPERTIES.md`.**

### Enforcement

One injectable `PlanLimitService` (in `service/internal`, resolves org → live subscription → plan via `SubscriptionReadService`) answers `assertCanAddUser(orgId)` (seat cap; employee rows are the seats) `assertStatsRangeAllowed(orgId, from)` and `assertAmcEnabled(orgId)` (the `amc_enabled` plan capability → `409 amc_not_in_plan`). Hard-limit breaches throw a coded `ConflictException` (409) so the UI can show an upgrade CTA; a missing live subscription on callable gates → `409 org_no_live_subscription`. Car-quota / invoice / report-window checks are added at new call sites rather than pre-grown. `subscription.status` in `past_due`/`expired` is handled separately (grace / read-only), independent of the numeric limits. See `docs/FEATURE-PLANS-CATALOG.md` for the full file map.

### Org invites & roles — `docs/FEATURE-INVITES.md` is the source of truth

**When working on invites, the manager/worker role model, the employee roster/quota, leaving an org, or `GET /api/me`'s `isManaged` flag, read and update `docs/FEATURE-INVITES.md`** — it holds the settled decisions (owner-only invites; **employees are the plan seat**: `employees` gains `email` + `user_id`, `org_invites` targets one roster row, `maxUsers` caps employee creation in `EmployeeCreateService` and is re-checked on send/accept; 7-day expiry; declined ≠ revoked; deleting an employee cascades — job-card assignments cleared, invite rows removed, linked account org-less; leave links live on `users.org_id` + `employees.user_id`), the API contract with error codes, UI instructions, and the file map for fast traversal. Built by migrations `004-invite-roles.sql` (`manager`/`worker` roles, `declined` invite status) + `005-employee-user-link.sql`; endpoints live in `InviteController` → `InviteService` (single workflow class) plus `POST /api/org/leave` in `UserService`. No email is sent — the invitee finds pending invites in-app via `GET /api/invites/me` and accepts (returns a fresh `org_id`-carrying token) or declines. Legacy `ADMIN`/`STAFF` role values remain until the model is revisited.
