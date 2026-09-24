# mygarageone-internal-console-api — build instructions

> Internal B2B console API for the devs and business owners of the Clean Cars platform.
> It shows **every** user, org, live plan, payment history, and per-org totals across
> the whole fleet. Read-only everywhere except one thing: managing the login whitelist.
>
> This doc is the complete working brief for a brand-new standalone repo. Scaffold the
> project from this file alone. It is written to feel like the sibling of
> `clean-cars-api` — same author, same conventions, same load-bearing rules — because
> it is the same author deliberately reusing them.

---

## 1. What it is (and is not)

- A **separate Spring Boot service**, separate repo, separate Firebase project, but it
  connects to the **same production `cleancars` MySQL schema** the main API uses.
- It is an admin/insights **read console**. There is no multi-tenancy to enforce here —
  the caller is a trusted internal user and queries span **all orgs globally**. The
  `AuthContext.requireOrgId()` rule from the main API does **not** apply; there is no
  org_id on the token to scope by (console users are not members of any tenant org).
- The **only** mutable resource is the login whitelist (`allowed_emails`). Everything
  else is `GET`-only by design in v1. Write endpoints for business data are a
  deliberate non-goal — the main API remains the single writer.
- **No payment processing, no webhooks, no Razorpay keys.** Money data is read from
  the tables the main API already maintains.

## 2. Stack — identical versions to clean-cars-api

| Thing | Version |
|---|---|
| Spring Boot | 4.1.1 |
| Java | 25 |
| Gradle (wrapper) | 9.7.1 |
| Spring Data JPA / Hibernate | same as main API |
| MySQL | 8 |
| Schema owner | Liquibase (`ddl-auto: none`) |
| Boilerplate | Lombok throughout |

Commands (mirror the main repo exactly):

```bash
# The service connects to the EXISTING clean-cars-api docker MySQL on :3370.
# Do NOT spin a second MySQL — reuse the main repo's:
#   (in clean-cars-api/)  docker compose up -d
./gradlew bootRun        # listens on :8099
./gradlew build
./gradlew test           # @SpringBootTest boots full context against :3370
./gradlew compileJava    # fast compile-only check
```

Port: **8099** (`server.port`, env `SERVER_PORT`) — clean-cars-api owns 8089.

## 3. Package layout — package-by-layer, same as the main API

```
controller / service / repository / entity / dto / security / config / exception
```

### Services are NOT the CRUD-four split

The four-service rule (`X{Create,Read,Update,Delete}Service`) exists because those
resources have full CRUD. Here everything is read-only, so read-only lookups stay
**single classes**, exactly mirroring the main API's rule for non-CRUD services
(`UserService`, `DashboardService`, ...):

- `OrgReadService`
- `UserReadService`
- `SubscriptionReadService`
- `PaymentReadService`
- `WhitelistService` (the only service with writes: add/remove a whitelisted email)
- `AuthService`, `FirebaseIdTokenService`, `JwtService`, `RefreshTokenService` — ported
  from the main API nearly verbatim (see §5).

## 4. The whitelist — the one custom gate

### Login flow (Firebase-only, same shape as the main API)

```
POST /api/auth/firebase   {"idToken": "<firebase id token>"}   -> {token, refreshToken}
```

1. `FirebaseIdTokenService` verifies the ID token against the **new** Firebase
   project's JWKS, issuer `https://securetoken.google.com/<project-id>`, audience, and
   expiry — same implementation as the main repo, pointed at a different project-id.
2. Resolve the caller's **lowercased** email from the token. If the email (or the
   Firebase UID/email mapping) is not present in `allowed_emails` →
   **`403 email_not_whitelisted`** and **no tokens are issued**. Treat anything not on
   the list as not allowed, full stop.
3. Whitelisted → issue HS256 access token + opaque refresh token (sha256-hashed in
   `refresh_tokens`, rotate-on-refresh, reuse-revokes-all — port this logic unchanged).

### The rules (settled, do not soften)

- The whitelist is the single source of truth for "can log in". A Firebase account
  that IS on the list gets in; one that isn't is rejected at login.
- A whitelisted user **can never be blocked or suspended**. There is no `blocked`
  column and no status. The *only* off-switch for an account is another logged-in,
  whitelisted user **removing that email** from `allowed_emails` — after which the
  next refresh of that user's tokens fails and access ends (existing short-lived
  access token expires naturally; refresh-token reconciliation on removal is a
  documented v1.1 nicety).
- Any logged-in whitelisted user may `POST` a new email or `DELETE` another — there
  are no roles among console users in v1.
- **You cannot remove your own email** → `409 cannot_remove_self`.

### Table (own migration baseline — does NOT touch main-app tables)

```sql
CREATE TABLE allowed_emails (
    id              BINARY(16)     NOT NULL PRIMARY KEY,   -- app-side @UuidGenerator
    email           VARCHAR(255)   NOT NULL,
    created_by_email VARCHAR(255)  NULL,                   -- who added it (audit)
    created_at / updated_at TIMESTAMP,                     -- DB-owned, UTC
    CONSTRAINT uq_allowed_emails_email UNIQUE (email)
);
```

- Email stored lowercase, trimmed; comparison is exact-match on the lowercased email.
- Seed: exactly **one** row, added in the baseline migration —
  `radhaiya.solutions@gmail.com`. This is the deliberate, documented exception to the
  "no seed data" rule (without it nobody could ever log in or add anyone).
- `created_by_email` is the audit trail for "who added whom" until a fuller audit log
  is ever requested.

## 5. Auth / tokens — cloned from clean-cars-api

- `/api/auth/**` is permit-all; **every other route requires a Bearer access token**
  and there is no other permit-all route (no reference-data-style public endpoints).
- Access token: HS256, `app.jwt.secret`, TTL `app.jwt.ttl-seconds`. Issuer
  `mygarageone-internal-console-api`. **Use a distinct secret and issuer from the main
  API** so a token meant for one service can never authenticate to the other, and the
  two apps can rotate/refresh independently.
- Refresh token: opaque random string, sha256 hash stored, `POST /api/auth/refresh`
  rotates; replaying a revoked token revokes all of that user's tokens
  (`TokenReuseException` + `noRollbackFor` pattern — port as-is).
- **Logout**: `POST /api/auth/logout` revoking the presented refresh token.
- Static `AuthContext` accessor (`require()`, optional info) exists for identity
  (userId/email/name) — but note again: no org scoping here.
- The user identity need not correspond to any row in the main `users` table. The
  console user exists only in the whitelist + token.

## 6. Data access — read the `cleancars` schema directly

Point the datasource at the same `cleancars` MySQL schema. Repositories here are
**query-only** (Spring Data finder methods / projections); no writes, no reads that
mutate. Two Liquibase behaviours matter:

1. This service's own changelog contains **only** its own tables (`allowed_emails`,
   its `refresh_tokens`). It must never `ALTER` a main-app table — if a main-app
   migration is needed for the console to read something new, it ships in the
   **clean-cars-api** repo.
2. Hibernate must have matching `@Entity` classes in this repo for any table it maps.
   Entities carry `@Getter` (and `@Setter` only where actually mutated) — never
   `@Data`/`@ToString`/`@EqualsAndHashCode` on an `@Entity`. IDs are read as
   `UUID` over `BINARY(16)`.

Everything in the main schema is org-tenanted — verified by reading the main repo's
entities — so per-org totals are plain `WHERE org_id = ?` counts:

| Entity | org-tenanted |
|---|---|
| `ServiceOrder` (`service_orders.org_id`) | yes |
| `Car` / `Bike` (`cars.org_id`, `bikes.org_id`) | yes |
| `Customer` / `Employee` / `Vendor` | yes |
| `Expense` / `ExpenseCategory` / `ServiceCatalog` / `ServiceCategory` | yes |
| `Subscription`, `RazorpayPayment`, `PaymentEvent` | yes (org-scoped via subscriptions / notes / joins) |
| `Organization`, `User` | the subject rows themselves |

Resolution path for the per-user view: `users.org_id → organizations` row, then
totals are plain `org_id` counts — no joins through subject links (safer than joining
a customer's cars when a few org-less edge rows exist).

**Timestamps: UTC in, same org-timezone-out policy as the main API** — JVM pinned to
UTC (`TimeZone.setDefault(UTC)` static block + `-Duser.timezone=UTC` in tests), the
serializer writes org-local wall time at the JSON boundary using the org's
`organizations.timezone` even for admin views. `LocalDate` carries no zone.

**Razorpay amounts** are paise everywhere read straight from tables built by the main
API (`razorpay_payments.amount_paise`, payment events) — convert to rupee decimals at
the JSON boundary exactly like `PlanService` does. Do not store prices locally; never
add a live Razorpay fetch here in v1.

## 7. Endpoints (all GET, plus whitelist management)

Pagination everywhere lists are returned: `PageResponse<T>` envelope, never raw
Spring Data `Page`. `spring.data.web.pageable.max-page-size: 50` caps `?size=`
globally; per-endpoint `@PageableDefault` sets defaults only. `?search=` is
case-insensitive contains where noted.

### Orgs

- `GET /api/orgs?search=&page=&size=` — one card per org: id, name, timezone,
  currency code + symbol, contact phone/email, createdAt, member count, live
  subscription status + plan name (`NONE` when none, mirroring
  `CurrentSubscriptionResponse` semantics: only live statuses count).
- `GET /api/orgs/{id}` — full detail: everything above plus the embedded user list
  (id, email, phone, role, trial_used, createdAt), the full live subscription row
  (status, dates, billing cycle, embedded plan block without pricing), totals summary
  (see `/stats`), and recent payment entries (last N, newest first).
- `GET /api/orgs/{id}/stats` — `{ totalCars, totalBikes, totalCustomers, totalEmployees,
  totalServiceOrders, totalServiceCatalog, totalExpenses }` — all-time `COUNT(*)` or
  sums, no date filter.

### Users

- `GET /api/users?search=&page=&size=` — across all orgs (search matches email/phone):
  id, email, phone, org id + name, role, `trial_used`, status, createdAt, **plus the
  user's plan state**: `planName`, `planStatus` (`TRIALING`/`ACTIVE`/`PAST_DUE`/
  `SUSPENDED`/`CANCELLED`/`EXPIRED`/`NONE` — same live-lifecycle read as
  `CurrentSubscriptionResponse`: only the most recent live-status subscription row
  counts, terminal/absent rows are `NONE`), `planExpiryDate` (that subscription's
  `endDate`; omitted when `NONE`), and `trialDaysRemaining` when trialing (omitted
  otherwise). Delegated to a `SubscriptionReadService.currentForOrg(orgId)` helper so
  this status logic is written once and reused by the org endpoints — never
  duplicated, never recomputed locally with stray date math ("plan expiry" is read
  off the row's `endDate`; no derived local expiry calculation in this service).
- `GET /api/users/{id}/org` — **the click-through**: the user's org, same response
  shape as `GET /api/orgs/{id}` (embedded users list, live subscription + embedded
  plan block without pricing, recent payments) resolved via
  `users.org_id → organizations → counts by org_id`. Returns `org: null` (not 404)
  for an org-less user — the console renders "no org yet". No totals endpoint of its
  own: `OrgTotals` is embedded in this response, of the same shape as
  `GET /api/orgs/{id}/stats` — `{ totalCars, totalBikes, totalCustomers,
  totalEmployees, totalServiceOrders, totalServiceCatalog, totalExpenses }`.

### Subscriptions (append-only history)

- `GET /api/subscriptions?orgId=&status=&page=&size=` — every subscription row
  newest-first, including terminal ones; each with the embedded plan block (no
  pricing — pricing belongs to a live Razorpay fetch, and this service takes no
  dependency on Razorpay availability).

### Payments

- `GET /api/payments?orgId=&page=&size=` — newest-first from `razorpay_payments`
  (payment id, subscription id, amount in rupee decimals, currency, status, paidAt)
  joined/filtered by org; plus `payment_events` audit rows for subscription lifecycle
  events on demand (`GET /api/payments/events?orgId=`).

### Whitelist management (the only writes)

- `GET /api/allowed-emails` — list with `createdByEmail`, `createdAt`.
- `POST /api/allowed-emails` `{email}` — normalizes to lowercase, `409
  email_already_whitelisted` if present; sets `createdByEmail` from the token.
- `DELETE /api/allowed-emails/{id}` — `409 cannot_remove_self` when it is the
  caller's own email.

### Error contract — RFC-7807 `ProblemDetail`, same handler map

`ApiExceptionHandler` ported verbatim: `NotFoundException` → 404,
`ConflictException` → 409 (machine-readable `code`), `MethodArgumentNotValidException`
→ 400 with `errors` map. Codes minted here: `email_not_whitelisted`,
`cannot_remove_self`, `email_already_whitelisted`. Nothing else — no org-missing
ambiguity (admin is global; an unknown id is just 404).

## 8. Config profiles & environment

`application.yml` (common) + `application-{local,stage,prod}.yml` + test override.
Triple-quoted-fail-closed rule from the main API: stage/prod **fail startup** when the
secret-class settings below are unset.

| Setting | local/dev | stage/prod |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | localhost:3370, root/root, db `cleancars` | env vars, fail-closed |
| `JWT_SECRET` | dev default | env var, fail-closed |
| `FIREBASE_PROJECT_ID` | `mygarageone-internal-console` | env var, fail-closed |
| `JWT_TTL_SECONDS` / `JWT_REFRESH_TTL_DAYS` | 3600 / 30 | same |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*`/`http://127.0.0.1:*` allowed patterns | env var, optional; unset = no cross-origin calls (fail-closed) |
| `SERVER_PORT` | 8099 | 8099 |

CORS wiring is identical in shape to the main API: `SecurityConfig` +
`CorsConfigurationSource` from `@ConfigurationProperties(prefix = "app.cors")`,
origin **patterns**, method/header/credentials/max-age shared in `application.yml`,
only `allowed-origin-patterns` per profile.

## 9. Conventions carried over verbatim from clean-cars-api

These load-bearing rules are **not re-derived here** — copy them straight from the
main repo's `CLAUDE.md` + `docs/ARCHITECTURE.md`; they apply unchanged:

- Package-by-layer folders (§3), four-service only where CRUD exists (here: none).
- DTOs are `record`s; `applyTo(entity)` pattern N/A (no create/update business DTOs).
- Lombok: entities `@Getter`/`@Setter`-only-where-needed/`@NoArgsConstructor`, services
  `@RequiredArgsConstructor` over `private final`, `@Slf4j` where logging happens; not
  on `SecurityConfig`/`JwtService` (constructor-built HMAC key from `@Value`s).
- Enums ↔ DB: lowercase in MySQL, UPPERCASE in Java, `@Converter(autoApply=true)`
  bridge; inbound JSON enums case-insensitive (`accept-case-insensitive-enums`).
- `PageResponse<T>` envelope everywhere, `max-page-size: 50`.
- Errors: domain exceptions → RFC-7807 (§7).
- Uniqueness pre-checked in service for a coded 409 **and** backed by a DB unique
  constraint (whitelist email).
- Schema changes only via new `NNN-*.sql` Liquibase migrations, never editing applied
  files; Hibernate `ddl-auto: none`; app owns UUID ids via `@UuidGenerator`; the DB
  owns timestamps; design doc `.dbml` incremented in this repo for its own tables.
- No DML by hand against the DB — everything through app code + migrations.

## 10. Explicitly not built in v1 (roadmap)

- **Any write to tenant data** — org/user/subscription mutations stay in clean-cars-api.
- **Charts / aggregation dashboards** — per-org counts only; time-series can reuse the
  main `ChartService` bucketing logic if a statistics page is ever wanted here.
- **Full audit log of whitelist changes** — `allowed_emails.created_by_email` covers
  "who added whom"; a `removed_by_email`/event table is a v1.1 follow-up.
- **Roles among console users** — v1 is flat whitelist. If a viewer/admin split is
  ever needed, it becomes an `allowed_emails.role` column (deny + optional role), not
  a Spring Security role model.
- **Live Razorpay fetch, invoice/payment-link tooling, dunning UI** — out of scope.
- **Emails to owners** — no email is ever sent this service.
