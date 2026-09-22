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
docker compose down -v          # wipe volume; next up re-migrates from scratch

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

# Build / test (each @SpringBootTest boots the full context against the Docker MySQL)
./gradlew build
./gradlew test
./gradlew test --tests 'com.example.cleancarsapi.CleanCarsApiApplicationTests'
./gradlew compileJava          # fast compile-only check

# Get a token for manual API calls — login is Firebase-only (Google/Apple/OTP), no password:
#   POST /api/auth/firebase  {"idToken":"<firebase id token>"}  -> {token, refreshToken}
#   then send  Authorization: Bearer <token>
# Getting a real Firebase ID token locally means signing in through the Firebase JS SDK
# (project: clean-cars-api) and reading the token off the client — see "Auth / tokens" below.
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
- **Login is Firebase-only** — `POST /api/auth/firebase` `{idToken}`. Firebase Auth is the single front door for every sign-in provider (Google, Apple, phone OTP) — whichever the client used, it always hands back a Firebase ID token, so this is the only login endpoint and the only identity verifier the backend needs. There is no password: `users.password_hash` never comes back. `FirebaseIdTokenService` verifies the token against Firebase's JWKS (`https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`), its issuer (`https://securetoken.google.com/<app.firebase.project-id>`) + audience + expiry, and (when an email claim is present at all) `email_verified`. `app.firebase.project-id` / env `FIREBASE_PROJECT_ID` — Firebase project is `clean-cars-api`.
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

`spring.jpa.hibernate.ddl-auto: none`. The schema's source of truth is the Liquibase changelog: `db/changelog/db.changelog-master.yaml` auto-includes every SQL-formatted file under `db/changelog/migrations/` in alphabetical order (`001-initial-schema.sql` is the full baseline — every PK/FK is a `BINARY(16)` UUID generated app-side by Hibernate's `@UuidGenerator`). Liquibase runs on every boot in every profile (local, stage, prod, test), applied once per database via `DATABASECHANGELOG`. `src/main/resources/cleancars_schema (1).dbml` is the design doc — keep it in sync with the changelog.

**Changing the schema means dropping a new `002-*.sql` (etc.) file into `db/changelog/migrations/` — never edit an already-applied file** (checksums). Hibernate entities must mirror the columns; the app owns id generation (`@UuidGenerator`), the DB owns timestamps.

**DDL only via changelogs against the local MySQL.** Running manual schema/table statements — `CREATE` / `ALTER` / `DROP` / `ADD` / `MODIFY` / `RENAME` — is not needed anymore; edit the changelog and restart. Do **not** run DML: no `INSERT`, `UPDATE`, or `DELETE` of data. `docker compose down -v` is the reset (the next boot re-migrates from scratch).

**No seed data.** The DB starts empty — plans, orgs, and users are all created through the app (`subscription_plans` rows are inserted manually by the operator, e.g. the one Trial row every account needs before `POST /api/subscription/trial` can work).

Timestamps: `@CreationTimestamp` / `@UpdateTimestamp` for app-managed rows; `@Column(insertable = false, updatable = false)` where the DB owns `created_at`/`updated_at`.

## Timestamps & timezones — UTC in, org timezone out

- The **JVM is pinned to UTC** — `CleanCarsApiApplication` has a static block (`TimeZone.setDefault(UTC)`) that covers every `main()`-launched run; `@SpringBootTest` skips `main()`, and the driver / Hibernate capture the JVM default before the app class ever initializes there, so the Gradle `test` task sets `-Duser.timezone=UTC` explicitly.
- MySQL `TIMESTAMP` columns store **UTC**; a `LocalDateTime` in the app always means **UTC wall time** (`LocalDateTime.now()`, `@CreationTimestamp`, entity read-backs — all UTC, no skew). The old "~5.5h shift" caveat below is resolved by this.
- `organizations.timezone` — an **IANA zone id** (`Asia/Kolkata`, `VARCHAR(64) NOT NULL`), a compulsory field the user sets when the org is created (`POST /api/subscription/trial` carries it; `ZoneId.of` rejects anything unknown with a 400). The column's `DEFAULT 'Asia/Kolkata'` only backfilled rows that predate the column.
- **Responses convert UTC → the caller's org timezone at the JSON boundary**: `TimezoneJacksonConfig` registers a Jackson 3 `ValueSerializer<LocalDateTime>` that writes a wall-time ISO string (`uuuu-MM-dd'T'HH:mm:ss`, **no offset** — chosen shape, the frontend shows it as-is). Unauthenticated or org-less calls get UTC. The org's zone is resolved once per request (memoised in a request attribute) from `AuthContext` → `organizations.timezone`.
- `GET /api/me` also exposes `orgTimezone` next to `orgName`; `GET /api/organization` returns it on the entity.
- **Pick-lists — `GET /api/reference`** (permit-all, `ReferenceDataController` → `ReferenceDataService`): `{ timezones: [{id, label}], currencies: [{code, symbol, label}] }`. Timezones are one hand-picked main zone per standard UTC offset (`ReferenceDataService.MAIN_ZONES`, 37 entries west → east — each offset such as +5:30 appears once), label `Asia/Kolkata (GMT+5:30)` using the **standard** (non-DST) offset. Saving still accepts any valid IANA id, not only the listed ones. The client shows `label`, sends `id` — only the IANA id is stored, never the label.
- `LocalDate` fields (trial `startDate`/`endDate`, chart `from`/`to`) carry no zone; "day" boundaries are UTC days. If business days should follow the org's zone instead, that's a separate, not-yet-requested change.

## Organization currency

`organizations.currency_code` (ISO 4217, `CHAR(3)`) + `currency_symbol` (e.g. `$`, `₹`), migration `002-organization-currency.sql` (existing rows backfilled `INR`/`₹`). Compulsory `currency` (code only) on `POST /api/subscription/trial`, optional on `PUT /api/organization` (null = unchanged). The server resolves code → symbol (`ReferenceDataService.requireCurrency` / `symbolOf`, English-locale symbol, falls back to the code) and stores both; `GET /api/organization` returns both. Allowed codes = currencies some country currently uses (JDK locale data), listed by `GET /api/reference` with label `USD ($)` (just `AED` when the symbol is the code). Unknown code → 400. Display-only for now — no amounts are converted; Razorpay billing stays INR.

## Config profiles

`application.yml` (common) + `application-{local,stage,prod}.yml` (main resources) + `application-test.yml` (test resources, activated by `@ActiveProfiles("test")`). Default profile is `local`; `SPRING_PROFILES_ACTIVE` overrides. `stage`/`prod` read `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` from the environment. `local` and `test` point at the Docker MySQL on `localhost:3370`.

## Subscription plans & billing

**Payments provider: Razorpay** (recurring subscriptions), wired for: pricing read side, checkout, and webhook sync. Razorpay is the **source of truth for money** — price, currency, billing interval, subscription charging, retries/dunning. The entitlements/limits stay in our DB (`subscription_plans` capability columns). `razorpay-saas-billing-entitlements.md` is the design doc the implementation follows.

- **Prices are never stored locally.** `subscription_plans.monthly_price` / `yearly_price` are **gone**; a Razorpay Plan is per-cycle, so each plan has `razorpay_monthly_plan_id` / `razorpay_yearly_plan_id` (nullable — a cycle the plan doesn't sell is NULL; the Trial row has both NULL). Amounts are fetched **live** from Razorpay by `PlanService` (`RazorpayGateway.fetchPlan`); Razorpay being unreachable fails `GET /api/plans` (500, fail-closed — no stale prices). The 8 real Razorpay plan IDs must be created in the Dashboard and filled into the live `subscription_plans` rows (`TODO(razorpay)`).
- **Config:** `app.razorpay.key-id` / `key-secret` / `webhook-secret` (`RazorpayProperties`, env `RAZORPAY_*`; stage/prod fail startup when unset). Test-mode keys locally.
- **Checkout:** `POST /api/subscription/subscribe` `{razorpayPlanId}` — the **Razorpay Plan ID** the client picked from `GET /api/plans` pricing (`pricing.monthly.razorpayPlanId` / `pricing.yearly.razorpayPlanId`); it encodes both the plan and the billing cycle, so `SubscriptionService.subscribe` resolves the internal row by matching whichever `razorpay_monthly_plan_id` / `razorpay_yearly_plan_id` column holds it (unknown id → 404; hiding a plan with `is_public = false` also 404s). `SubscriptionService.subscribe` creates the Razorpay subscription (`total_count = 100` — Razorpay has no unlimited value, so a large fixed cycle count stands in for "until cancelled"; notes carry `org_id`/`user_id` for webhook tracing) and a local row with `razorpay_subscription_id`, `status = pending`. Returns the Razorpay subscription id + our key id for `checkout.js`. Subscribing while already ACTIVE/PAST_DUE/PENDING → `409 org_already_subscribed`; converting a live trial is **allowed** — when Razorpay activates the paid sub the webhook supersedes (`CANCELLED`) the trialing row (trial ends early; it was free).
- **Webhooks:** `POST /api/webhooks/razorpay` — permit-all (`SecurityConfig`), authenticated instead by the `X-Razorpay-Signature` HMAC (`app.razorpay.webhook-secret`) checked before anything else; invalid → 401. Processing (`RazorpayWebhookService`, `@Transactional(noRollbackFor = RazorpayWebhookException.class)` — same pattern as the token-reuse lockout so the FAILED event row survives the rethrow): dedupe on `payment_events.razorpay_event_id` UNIQUE (PROCESSED/IGNORED duplicates → 200 no-op; FAILED rows are updated and re-run on Razorpay's retry), raw payload appended to `payment_events` idempotently, then the status matrix: `activated/charged/resumed` → `active` (+ period dates from `current_start`/`current_end`, + trial supersede), `pending` → `past_due` (grace), `halted` → `suspended`, `cancelled` → `cancelled`, `completed/expiry` → `expired`; `payment.authorized/captured/failed` only snapshot into `razorpay_payments` (keyed by `razorpay_payment_id` UNIQUE, upserted — amount in paise, currency, status, paid_at) and make **no access decision by themselves** — the subscription lifecycle event does. A webhook referencing an unknown in-flight subscription → recorded FAILED + rethrown → non-200 → Razorpay retries. Payment events make no access decision — the subscription lifecycle event does.
- **Plan change (up/downgrade):** `POST /api/subscription/change-plan` `{razorpayPlanId}` on the org's ACTIVE subscription → `PATCH /v1/subscriptions/{id}` `{plan_id, schedule_change_at: "now"}`. Razorpay does all proration: upgrades charge only the remaining amount (differential invoice on the saved autopay), downgrades refund the difference — no new checkout, no money math in our code. The local row's `plan_id`/`billing_cycle` flip on the `subscription.updated` webhook (`RazorpayWebhookService.applyPlanMapping`; `subscription.charged` also re-syncs the mapping from the payload's `plan_id`, covering cycle-end scheduled changes). Requires ACTIVE (not PAST_DUE — Razorpay rejects plan changes in `pending`); same plan+cycle → `409 plan_change_same_plan`.
- **Status:** `subscriptions.status` is one DB ENUM extended to `trialing|pending|active|past_due|suspended|cancelled|expired` (+`SubscriptionStatus` enum / converter); there is no separate razorpay_status vs access_status yet — one column maps Razorpay state → access semantics, split later only if a real need appears. Unknown Razorpay plan subscription chain (no `cancel_at_period_end` flag stored yet).
- **Period dates are Razorpay's**, from the webhook payload (`current_start`/`current_end` epoch seconds) — not derived from our clock.
- **Not yet built:** Razorpay SDK-independent (plain `RestClient`, no `razorpay-java`); refunds, proration, invoice sync, `subscription.cancelled` initiated by us (cancel-at-period-end policy), `payment_link`/`payment.page` variants, retry/dunning UI states beyond the status matrix, and the Dashboard-side webhook registration (the `X-Razorpay-Event-Id` header — falls back to a SHA-256 body hash when absent).

### `GET /api/plans`

The first plan API — read-only, behind auth, **not org-scoped** (`PlanController` → `PlanService.listPublic()`). Returns `is_public` plans ordered by `sort_order`, as `PlanResponse`:

- `pricing` = `{ monthly: { razorpayPlanId, amount, currency }, yearly: { razorpayPlanId, amount, currency } }` — amounts **fetched live from Razorpay** (paise → rupee decimals), one fetch per offered cycle; a cycle the plan doesn't sell is **absent** from `pricing` (Trial has no `pricing` block at all). The **monthly/yearly toggle** on the UI just picks `pricing.monthly` vs `pricing.yearly`. Razorpay unreachable → the whole endpoint 500s (fail-closed, no stale prices).
- `isTrial` / `trialDays` (the latter `null` unless `isTrial`), nested `limits` {maxUsers, maxCars} and `features` {reportWindowMonths, statsRangeYears, statisticsPage, invoiceGeneration}; a `null` number = unlimited (and `non_null` serialization omits the key).

Prices moved to Razorpay entirely (the old local `pricePerMonth`/`savingsPercent` derived fields went with them — Razorpay won't tell us the monthly price of a yearly-only plan). The 5 plan rows are inserted manually by the operator (no seed data); `GET /api/subscription`'s embedded `plan` block carries **no pricing** (pricing belongs to `GET /api/plans` only — embedded blocks don't trigger a Razorpay fetch per subscription read).

### The five plans (Trial, then P1 → P4 lowest → highest)

| # | Name | Monthly / Yearly (INR, set on Razorpay — not stored locally) | Report-download window | Statistics page | Invoice generation |
|---|------|------|------------------------|-----------------|--------------------|
| — | **Trial** | — (free, one-time) | last **1 month** | **1 year** | **yes** |
| P1 | **Starter**    | ~499 / ~4990   | last **1 month**   | — (none)      | no  |
| P2 | **Workshop**   | ~999 / ~9990   | last **4 months**  | **1 year**    | no  |
| P3 | **Pro**        | ~1999 / ~19990 | last **12 months** | **3 years**   | no  |
| P4 | **Enterprise** | ~4999 / ~49990 | **unlimited** | **unlimited** | **yes** |

Yearly ≈ 10× monthly (~2 months free), configured on the Razorpay Dashboard when the 8 plan IDs are created. A cycle a plan doesn't sell gets `razorpay_*_plan_id = NULL` and is absent from `GET /api/plans` pricing (Trial always is — a one-time free trial has no billing cycle to pick).

**Trial is a real plan row, not a per-plan modifier.** `subscription_plans.is_trial` is `true` for exactly this one row (`max_users`/`max_cars` deliberately small; `report_window_months`/`stats_range_years`/`invoice_generation` all unlocked like Enterprise so the trial previews every *feature*, just at reduced *limits*). There used to be a `trial_days` column letting any plan optionally offer a trial (Starter/Workshop/Pro at 14 days, Enterprise at 0) — that's gone; the fixed length now lives as `SubscriptionService.TRIAL_DAYS` (14) since there's exactly one trial offering.

### Plan capability columns on `subscription_plans`

Explicit nullable columns (not a `features` JSON blob), `null` = unlimited:

- `report_window_months`: `1` \| `4` \| `12` \| `null`
- `stats_range_years`: `0` (stats page hidden) \| `1` \| `3` \| `null`
- `invoice_generation`: `false` \| `true`
- `max_users` (hard limit, enforced at invite/activate), `max_cars` (soft limit — warn, don't block)
- `is_public` (hide without deleting), `sort_order` (pricing-page order), `is_trial` (exactly one row)

Add more `max_*` / capability columns only when a plan actually needs one.

### Users ⇄ orgs — 1 user : 1 org

A user is created **org-less** (`users.org_id` nullable, no org at sign-up). The org is born the first time they **start a trial or buy a plan** — org details come in that request body, and no standalone `POST /api/orgs` exists. `users.org_id → organizations.id` is the only link (`organizations` has no `owner_id`); the owner is simply the member with `users.role = 'owner'`. One account = one org — a second org means a second account.

### Trial — `POST /api/subscription/trial`

Body `{ orgName, timezone, currency, contactPhone?, contactEmail?, address? }` — **no `planId`**: there's exactly one Trial plan, resolved automatically (`SubscriptionPlanRepository.findByIsTrialTrue()`). `timezone` is compulsory (the org's IANA display zone — see "Timestamps & timezones"). The caller must be an **org-less** authenticated user. `SubscriptionController` → `SubscriptionService.startTrial` (single class, not the CRUD-four split). In one transaction it: creates the `Organization`, links the user (`users.org_id` + `role = 'owner'`), opens a `trialing` subscription against the Trial plan, sets `users.trial_used`. Returns **`StartTrialResponse`** = `{ subscription: SubscriptionResponse, token, tokenType, expiresIn }` — a fresh access token carrying the new `org_id` (the caller's old token has none; the existing refresh token stays valid).

- Caller already has an org → `409 user_already_has_org`.
- **One trial per account, ever** — `users.trial_used` boolean (`users.email` is globally `UNIQUE` → one email = one user row). Already true → `409 trial_already_used`. Concurrent starts serialise on a `PESSIMISTIC_WRITE` lock of the user row (`UserRepository.findByIdForUpdate`).
- Subscription row: `status = trialing`, `start_date = today`, `end_date = today + SubscriptionService.TRIAL_DAYS` (14). `subscriptions` is **append-only history** (no `UNIQUE(org_id)` — would block resubscribe / plan-change). `SubscriptionStatus` enum ↔ lowercase DB via `SubscriptionStatusConverter`.
- Trial end (not converted) → `expired` → org read-only. The expiry job is **not built yet**.

### Current plan — `GET /api/subscription`

Any authenticated user. **Always 200** — `CurrentSubscriptionResponse` with `active` / `onTrial` booleans, `status` (`"NONE"` or a live `SubscriptionStatus` name), `subscriptionId` / `startDate` / `endDate` / `daysRemaining`, and a **full embedded `plan`** block (same shape as `GET /api/plans`, **minus pricing** — prices live in `GET /api/plans` only). Org-less caller, or no live subscription → `{ active: false, onTrial: false, status: "NONE" }` (other fields omitted by `non_null`). Reads the most recent subscription in `TRIALING` / `ACTIVE` / `PAST_DUE`; terminal rows are treated as "none".

### Plan usage on `GET /api/me`

`UserProfile.plan` (a `PlanUsage`, not the full `PlanResponse`) gives a slim max-vs-current view for showing quota bars on the UI without a second call: `{ planName, isTrial, billingCycle, maxUsers, currentUsers, maxCars, currentCars, reportWindowMonths, statsRangeYears }`. `billingCycle` is the cycle sold (`MONTHLY`/`YEARLY`, persisted on `subscriptions.billing_cycle` at subscribe time); null while on trial. `maxUsers`/`maxCars`/`reportWindowMonths`/`statsRangeYears` are `null` for unlimited (same convention everywhere else; `statsRangeYears = 0` means the statistics page is hidden entirely); `current*` is a live `COUNT(*)` (`UserRepository`/`CarRepository`.`countByOrgId`) — not cached, not part of `PlanLimitService`. `reportWindowMonths`/`statsRangeYears` are read straight off the plan (no "current usage" concept for those, unlike users/cars). `plan` is `null` (omitted by `non_null`) for an org-less caller **or** one whose org has no live subscription (mirrors `CurrentSubscriptionResponse.active`) — `UserService.getProfile` delegates to `SubscriptionService.getCurrentForOrg(orgId)` for that check rather than duplicating it.

### Charts — `GET /api/charts`

`ChartsController` → `ChartService` (single class, not a CRUD resource). Query params: `metric` (`TOTAL_SERVICE` | `TOTAL_REVENUE` — `TOTAL_EXPENSE` not built yet as a bucketed metric, though the `Expense` entity now exists and is used by `GET /api/charts/kpi-tiles` below), `granularity` (`DAY` | `WEEK` | `MONTH` | `YEAR`), `from` / `to` (`LocalDate`, both required, inclusive). Returns `List<ChartBucket>` (`{ periodStart, periodEnd, value }`), one bucket per calendar-aligned period covering the whole range, zero-filled where there's no data — `WEEK` is the ISO week (Mon–Sun), `MONTH`/`YEAR` are calendar periods; the first/last bucket may extend slightly past `from`/`to` to stay calendar-aligned.

- `TOTAL_SERVICE`: count of non-`CANCELLED` service orders per bucket, keyed by `ServiceOrder.createdAt`.
- `TOTAL_REVENUE`: sum of `GstBreakdown.net()` (GST-excluded) across every line item of every **paid** order per bucket — unpaid and cancelled orders don't count.
- **Gated by `stats_range_years`** (inline in `ChartService`, no `PlanLimitService` yet — see Enforcement below): no live subscription, or `stats_range_years = 0` → `409 statistics_not_available`; `from` reaching further back than the plan's `stats_range_years` → `409 stats_range_exceeded`. `null` = unlimited, no check. `from > to` → `400`.

**Real data, not mocked** — bucket values are computed live (`serviceCounts` / `revenueTotals` in `ChartService`, joins `ServiceOrderRepository.findCreatedAtForServiceCount` / `findPaidRevenueLines`); the earlier `resources/mock/chart-mock-data.json` stand-in is gone. (The historical "createdAt reads back ~5.5h shifted" caveat is resolved by the UTC policy above — the JVM runs in UTC, so `LocalDateTime.now()`, stored values and read-backs all agree.)

### KPI tiles — `GET /api/charts/kpi-tiles`

`ChartsController.getKpiTiles` → `ChartService.getKpiTiles(orgId, from, to)`. Query params: `from` / `to` only (no metric/granularity — a single flat total, not buckets). Returns `KpiTilesResponse { totalRevenue, totalExpenses, totalProfit }` — **real data, not mocked**, a P&L tile rather than the operational counts it started as (those moved to `GET /api/charts/totals`).

- `totalRevenue`: same definition as `TOTAL_REVENUE` on `GET /api/charts` — `GstBreakdown.net()` summed across every line item of every **paid** order in range.
- `totalExpenses`: same net-of-GST calculation across every `expenses` row in range (via the new `ExpenseRepository.findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan`) — expenses have no paid/unpaid concept, every row counts.
- `totalProfit = totalRevenue - totalExpenses`, both net of GST (GST collected is a liability not income; GST paid is generally reclaimable input credit not a real cost) — resolves the `totalProfit` TODO left from the Expenses work.

Same `from > to` → `400` and `stats_range_years` gating (`409 statistics_not_available` / `409 stats_range_exceeded`) as the buckets endpoint — both share `ChartService.enforceStatsRange`.

### Org totals — `GET /api/charts/totals`

`ChartsController.getTotals` → `ChartService.getTotals(orgId)`. No params, **not gated by `stats_range_years`** (unlike the two endpoints above — these are basic counts, not statistics history). Returns `OrgTotalsResponse { totalCars, totalServices, totalEmployees, totalCustomers }`, all-time `COUNT(*)`, no date filter. `totalServices` is **the service catalogue size** (`service_catalog` row count, i.e. how many services the org offers), not a count of job cards — confirmed with the user. Originally lived on `GET /api/dashboard`; moved here on request.

### Expenses — `GET/POST/PUT/DELETE /api/expenses`, `/api/expense-categories`

Full CRUD resource pair: `ExpenseCategory` (`name`, unique per org) is a pure **label dropdown** for the caller's org, and `Expense` is one row per expense entry (`categoryName`, `amount`, `gstPercentage`, `gstIncluded`, `quantity`, `notes`). Only these inputs are stored and echoed back — no derived `unit*`/`line*` fields on the response; the net/GST/gross math (`GstBreakdown`) is used only server-side, by the charts/KPI totals.

The category on an expense is a **denormalized string, not an id/FK** (`expenses.category_name`): many rows may share a label (ten "Salary" entries a day are ten rows), and deleting a category only removes it from the dropdown — historical expenses keep the name intact. There is no "uncategorized" state (`category_name` is NOT NULL); create/update validate via `ExpenseCreateService.requireCategoryInOrg` that the name matches one of the org's labels (case-insensitive, 404 if not — stops typos without a DB FK). `GET /api/expenses` is a flat newest-first list (`?search=` matches `category_name`/notes); POST creates one row, PUT/DELETE are per expense id. There is no vendor link and no separate "expense date" — like `ServiceOrder`, `createdAt` is the only business timestamp. Renaming a category deliberately does **not** rewrite old expense rows.

`totalProfit`/`totalExpenses` are now wired on `GET /api/charts/kpi-tiles` (see below). Still not wired: `TOTAL_EXPENSE` as a bucketed metric on `GET /api/charts` itself.

### Dashboard — `GET /api/dashboard`

`DashboardController` → `DashboardService` (single class, not CRUD, no query params). The org's home-page snapshot, real data, **not gated by `stats_range_years`** — unlike charts/KPI tiles, this is the always-available operational overview, not the "statistics" premium feature. `totalCars`/`totalServices`/`totalEmployees`/`totalCustomers` used to live here — moved to `GET /api/charts/totals` on request; `DashboardService` no longer depends on `CarRepository`/`CustomerRepository`/`EmployeeRepository`/`ServiceCatalogRepository`.

- `servicesInProgress`: all-time count of `service_orders` with `status = IN_PROGRESS`.
- `servicesUnpaid`: all-time count of `paid = false` orders, excluding `CANCELLED` (a voided job isn't money owed).
- `todayRevenue` / `yesterdayRevenue`: `{paid, unpaid}` — net-of-GST sums of that day's order line items, split by the order's `paid` flag, keyed by `ServiceOrder.createdAt` (same convention as charts/KPI). `unpaid` uses a new `ServiceOrderRepository.findUnpaidRevenueLines` (mirrors `findPaidRevenueLines`, also excludes `CANCELLED`).
- `monthlyEarnings`: 12 entries for the current calendar year, `{month, amount}` — `amount` is **paid-only** net-of-GST revenue (this is "earnings", i.e. money actually received, not outstanding). `null` for any month after the current one; `0.00` for a past/current month with no data.

### Enforcement

One injectable `PlanLimitService` resolves org → active `subscription` → `plan` and answers `assertCanAddUser(orgId)`, `checkCarQuota(orgId)`, `reportWindowMonths(orgId)`, `statsRangeYears(orgId)`, `canGenerateInvoice(orgId)`. Hard-limit breaches throw a coded `ConflictException` (409) so the UI can show an upgrade CTA. `subscription.status` in `past_due`/`expired` is handled separately (grace / read-only), independent of the numeric limits.
