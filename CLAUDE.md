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

## Docs are part of "done" — keep them current

**Whenever a change alters behaviour, an endpoint's request/response shape, a rule, an error code, a migration, config, or the file map, update the docs in the same change:** this `CLAUDE.md` (it must be updated itself when conventions, commands, the feature list or any section here goes stale) **and every feature doc it touches** — the matching `docs/FEATURE-*.md` (rules, API contract, error codes, file map), `docs/ARCHITECTURE.md` when structure/conventions change, and the DBML design doc for schema changes. Add a new `docs/FEATURE-*.md` (and a pointer here) when you add a feature. Never leave a doc describing the old behaviour, and don't finish a task with the docs unchecked.

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
# (project: mygarageone-test) and reading the token off the client — see `docs/ARCHITECTURE.md` → "Tokens".
```

There is no linter configured.

**Sandbox / network:** in this environment the default Bash sandbox blocks outbound network, so `./gradlew` cannot download the Gradle distribution or dependencies. Run all Gradle commands with the sandbox disabled.

## Canonical convention doc

`docs/ARCHITECTURE.md` is the source of truth for how code is organised here — read it before adding a resource. It holds the details (package layout, the four-service CRUD split, multi-tenancy, auth/tokens + Firebase login, errors, enums ⇄ DB, pagination, CORS, Lombok); the rules you must not forget:

- **Package-by-layer** (`controller / service / repository / entity / dto / security / config / exception`).
- **Four `@Service`s per CRUD resource** — `X{Create,Read,Update,Delete}Service`, never a fat `XService`; `Read` owns single-get + list; shared field-copy lives on the request DTO as `applyTo(entity)`. Reference impl: `customer`, `car-*`.
- **Multi-tenancy:** every business row has `orgId` set once from the token via `AuthContext.requireOrgId()` — never from body/path/query; finders are org-scoped; a miss is a 404 (a caller can't tell "wrong org" from "doesn't exist").
- **Login is Firebase-only** (`POST /api/auth/firebase`, no password); refresh tokens rotate and reuse revokes the token **family**.
- **Errors:** throw a domain exception; `ApiExceptionHandler` maps it to an RFC-7807 `ProblemDetail`; uniqueness is pre-checked for a coded 409 *and* backed by a DB constraint.
- **Entities:** never `@Data`/`@ToString`/`@EqualsAndHashCode`; DTOs are `record`s. MySQL ENUMs are lowercase, Java constants UPPERCASE (JPA converter). Lists return `PageResponse<T>`; `max-page-size` is 50.

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

## Config profiles

`application.yml` (common) + `application-{local,docker,stage,prod}.yml` (main resources; `docker` = the `backend-service-docker-compose.yml` stack, DB/secrets via `scripts/.dev.env`) + `application-test-api.yml` (test resources, activated by `@ActiveProfiles("test-api")`). There is **no default profile** — `SPRING_PROFILES_ACTIVE` is required and startup fails without it (`CleanCarsApiApplication.main`); for local dev put `SPRING_PROFILES_ACTIVE=local` in `scripts/.dev.env` (bootRun loads it) or in the IDE run config. `stage`/`prod` read `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` from the environment. `local` points at the Docker MySQL on `localhost:3370`. `test-api` uses in-memory H2 (MySQL mode): Liquibase is disabled (the migrations are MySQL-only), Hibernate builds the schema (`create-drop`), and `src/test/resources/data.sql` adds the column defaults and seed rows the migrations normally provide — when a new NOT NULL DEFAULT column breaks a raw-SQL test insert, add its default there.

### STRICT: Keep `backend-service-docker-compose.yml` and `scripts/.dev.env` in sync with config

**Whenever a config change touches environment, profiles, ports, DB, or migrations, update `backend-service-docker-compose.yml` and `scripts/.dev.env` in the same change — they must always be current and complete.** That means: a new/renamed/removed env var or `app.*` property in `application*.yml` (add the matching key to `.dev.env`, mirroring the required-vars list in `application-test.yml` / `application-prod.yml`, and keep the comments in sync); a changed DB name/credentials/port, MySQL version, Liquibase image or connector version, changelog path, `SERVER_PORT`, or the Dockerfile; or a new service the backend depends on (add it to the compose file with `depends_on`). After editing, verify with `docker compose -f backend-service-docker-compose.yml config -q`. `.dev.env` holds real secrets and is gitignored — never print its values in chat, and keep the compose file's header comments accurate.

## Feature docs — read the matching one before touching the feature

Each doc holds the rules, API contract, error codes, migrations and file map; this file keeps only the cross-cutting conventions. The UI repo (`../clean-cars-ui`) has a counterpart in `docs/features/` for most of them — **when a contract changes, update the API doc and the UI doc together**.

| Area | API doc | UI counterpart (`clean-cars-ui/docs/features/`) |
|---|---|---|
| Plans, quotas, `GET /api/plans`, `/api/me` `plan` | `FEATURE-PLANS-CATALOG.md` | `plans.md` |
| Trial / subscribe / Razorpay / webhooks / change-plan | `subscription-flow.md` (+ design doc `razorpay-saas-billing-entitlements.md`) | `plans.md`, `onboarding.md` |
| Org-less users & trial onboarding | `frontend-org-onboarding.md` | `onboarding.md` |
| Charts, KPI tiles, totals, dashboard, top customers | `FEATURE-CHARTS-DASHBOARD.md` | `statistics.md`, `dashboard.md` (+ `../dashboard-api-spec.md`) |
| Expenses | `FEATURE-EXPENSES.md` | `expenses.md` |
| Payments (one-time / split), revenue math | `FEATURE-SPLIT-PAYMENTS.md` | `service-order.md` |
| AMC, line discounts | `FEATURE-AMC.md` | `amc.md` |
| Invoices (service + AMC), org invoice look | `FEATURE-INVOICES.md` | `service-order.md`, `amc.md`, `org-info.md` |
| Bikes | `UI-BIKES.md` | `vehicles.md` |
| Invites, roles, employee seats, leaving an org | `FEATURE-INVITES.md` | `employee.md` |
| Feature properties (maintenance, login gate, export) | `FEATURE-PROPERTIES.md` | `auth-and-login.md` |
| Timezones | `FEATURE-TIMEZONES.md` | `lib-utils.md` (`tz.ts`) |
| Org currency, tax name, distance unit | `FEATURE-ORG-CURRENCY-TAX.md` | `org-info.md` |
| Org phone country code | `FEATURE-ORG-PHONE-COUNTRY-CODE.md` | `org-info.md` |
| Digital inspection (**proposal, not built**) | `FEATURE-DIGITAL-INSPECTION.md` | `digital-inspection.md` |
| Internal console | `INTERNAL-CONSOLE-FRONTEND-INTEGRATION.md`, `internal-service-md.md` | (`internal-mygarageone` repo) |

Cross-cutting rules that live here because they span features:

- **Plan catalog & enforcement:** one `PlanLimitService` (in `service/internal`) is the chokepoint — `assertCanAddUser`, `assertSeatStillValid`, `assertAmcEnabled`, `assertStatsRangeAllowed`, plus the non-throwing `currentPlan`. Hard breaches throw a coded 409 so the UI can show an upgrade CTA. Car-quota / invoice / report-window checks are added at new call sites rather than pre-grown. `past_due` / `expired` (grace / read-only) is handled separately. Details: `FEATURE-PLANS-CATALOG.md`.
- **Billing:** Razorpay is the source of truth for money (price, currency, cycle, charging, retries); the DB holds entitlements only, and prices are never stored. `subscription-flow.md` §13 has the config and implementation rules.
- **Revenue:** money actually *received* counts (`OrderRevenueReader` / `paidNet`), per-line discounts come off before tax (`TaxBreakdown.ofLine`), AMC sales are a separate stream (net on reporting surfaces, gross on vehicle / customer detail).
- **Timestamps:** the JVM and MySQL run in UTC; entity `LocalDateTime`s are UTC and are converted to the org timezone only at the JSON boundary (`TimezoneJacksonConfig`). Odometer values are whole **meters**; display units are the UI's job.
- **Org settings are display-only labels** (currency, tax name, phone country code, distance unit): amounts / rates / stored numbers are never rewritten. The per-line tax *rate* always stays on the line item.
- **Roles:** `owner` / `manager` / `worker` (legacy `ADMIN`/`STAFF` remain in the enum); `AuthContext.require(UserRole.…)` gates endpoints. Employees are the plan seat (`FEATURE-INVITES.md`).
- **No-seed DB:** plans, orgs and users are created through the app; `subscription_plans` rows are inserted manually by the operator.

### Gating inside combined (single-call) responses

Detail endpoints embed several sections (vehicle → AMCs + owner contact; service order → invoice flag; customer → roll-ups). A section's entitlement is enforced **server-side, per section** — the client never decides what to hide.

- **Plan-gated section** (e.g. AMC via `amc_enabled`): two entry points on the service. `assertXEnabled(orgId)` throws the coded `409` and guards the *standalone* endpoint (`GET /api/amc-subscriptions`). The *embedding* path uses a soft variant that returns an empty value instead of throwing (`AmcSubscriptionReadService.listForVehicleOrEmpty` via `PlanLimitService.currentPlan`), because failing a whole detail page over one optional section is wrong. Contract: `[]` = "enabled, none yet"; a gated section that must be told apart from "none" should be `null`/omitted. The UI still reads `me.plan.*` to decide whether to render it.
- **Role-gated endpoint** (owner-only etc.): `AuthContext.require(UserRole.…)` in the controller/service → 403.
- **Role-gated fields in a shared response:** read the role from `AuthContext` while assembling and leave the fields out (`null`) for roles without access. Hiding in the UI alone is never enough.
- Build each combined response in one place (an `Assembler` / the `*ReadService`) so every gate lives in one spot. Roll-up numbers (revenue, counts) must apply the same gate (e.g. `amcRevenue` is `0` without AMC).
