# Internal Console API — frontend integration guide

The internal B2B console (dev/business-owner insights) is **built into**
`clean-cars-api` — one app, one port (**:8089**), one Firebase project
(`clean-cars-api`). Console routes live under `/internal/api/**` alongside the
tenant `/api/**` routes. Every view is **global across all orgs** (no tenant
scoping, no `org_id`) and read-only, except the one mutable resource:

- **Login whitelist** (`allowed_emails`) — the only writes in v1.

Everything else: `GET`-only org/user/subscription/payment inspection.

---

## 1. Auth — a separate token world

The console has its **own** HS256 secret and issuer (`clean-cars-api-internal`),
distinct from the tenant API's. A tenant Bearer token never opens `/internal/**`
(401) and an internal token never opens `/api/**` — the server enforces this with
two security chains. Sign in through Firebase exactly like the tenant app, then
swap the Firebase ID token for a console token pair.

### Login — `POST /internal/api/auth/firebase` (no auth)

```bash
curl -X POST http://localhost:8089/internal/api/auth/firebase \
  -H 'Content-Type: application/json' \
  -d '{"idToken":"<firebase id token>"}'
```

**200** → `{ token, tokenType: "Bearer", expiresIn, refreshToken }`

- Getting a real Firebase ID token means signing in through the Firebase JS SDK
  (Google / Apple / phone OTP) and reading it off the client — see the main
  repo's auth section. There is no password login.
- The **email on the Firebase account is the gate**: if it is not in
  `allowed_emails` (lowercased, trimmed) → **403**, detail
  `email_not_whitelisted`, and no tokens are issued.

**401** — invalid/expired Firebase ID token (ProblemDetail).

```js
import { getAuth, signInWithPopup, GoogleAuthProvider } from 'firebase/auth';

const credential = await signInWithPopup(getAuth(), new GoogleAuthProvider());
const idToken = await credential.user.getIdToken();
const res = await fetch('/internal/api/auth/firebase', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ idToken })
});
const { token, refreshToken, expiresIn } = await res.json();
// store both; send  Authorization: Bearer <token>  on every console call
```

### Refresh — `POST /internal/api/auth/refresh` (no auth)

```bash
curl -X POST http://localhost:8089/internal/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refresh token>"}'
```

**200** → the same `{ token, tokenType, expiresIn, refreshToken }` shape. The
presented refresh token is **rotated** — it is single-use; store the new one.
Replaying a revoked refresh token revokes **all** of that user's sessions (401).

> The whitelist is re-checked on every refresh too. If the account's email was
> removed from the whitelist, the next refresh fails (`403
> email_not_whitelisted`) — that is the intended off-switch. An already-issued
> access token expires naturally (TTL default 3600s).

### Logout — `POST /internal/api/auth/logout` (no auth)

```bash
curl -X POST http://localhost:8089/internal/api/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refresh token>"}'
```

**204**. Idempotent — unknown/already-revoked tokens are fine.

### Access token claims / identity

The internal token carries: `sub` (Firebase uid — **not** a UUID), `email`
(the whitelisted address), `name`. There is no `org_id`, no `role`, and no
`users` row behind it — the console shows data for **all** orgs.

---

## 2. Endpoint reference

Every route below (`except auth`) needs `Authorization: Bearer <token>`.

| Endpoint | Purpose |
|---|---|
| `GET /internal/api/orgs` | All orgs, searchable, paginated |
| `GET /internal/api/orgs/{id}` | Org detail (members, plan, totals, recent payments) |
| `GET /internal/api/orgs/{id}/stats` | All-time per-org counts |
| `GET /internal/api/users` | All account users, searchable, paginated |
| `GET /internal/api/users/{id}/org` | Click-through to the user's org |
| `GET /internal/api/subscriptions` | Subscription history (all orgs, terminal rows included) |
| `GET /internal/api/payments` | Razorpay payments (all orgs) |
| `GET /internal/api/payments/events` | Webhook event audit rows |
| `GET /internal/api/allowed-emails` | Whitelist list |
| `POST /internal/api/allowed-emails` | Add an email (the only writes, besides delete) |
| `DELETE /internal/api/allowed-emails/{id}` | Remove an email |

### Orgs

```bash
curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8089/internal/api/orgs?search=acme&page=0&size=20'
```

**`GET /internal/api/orgs`** — `PageResponse<OrgSummary>`, newest first.

OrgSummary fields:

| Field | Notes |
|---|---|
| `id`, `name`, `timezone` (IANA), `currencyCode`, `currencySymbol` | |
| `contactPhone`, `contactEmail` | nullable |
| `createdAt` | string, see "Timestamps" below |
| `memberCount` | users in the org |
| `subscriptionStatus` | live subscription status (`TRIALING`/`ACTIVE`/`PAST_DUE`) or `NONE` |
| `planName` | the live plan's name; omitted when `NONE` (`non_null` serialization) |

`?search=` matches name / contact email / contact phone (case-insensitive contains).

**`GET /internal/api/orgs/{id}`** — `OrgDetail`: everything on the card plus

| Field | Notes |
|---|---|
| `subscriptionStatus` | same semantics → `NONE` when no live row |
| `subscription` | the live row (see Subscription shape); omitted when `NONE` |
| `users` | the org's member accounts |
| `totals` | `{ totalCars, totalBikes, totalCustomers, totalEmployees, totalServiceOrders, totalServiceCatalog, totalExpenses }` — plain counts, no date filter |
| `recentPayments` | newest 10 payments (see Payment shape) |

Unknown org id → plain **404** (admin sees everything; no tenant ambiguity).

**`GET /internal/api/orgs/{id}/stats`** — the same `totals` object on its own.

### Users

```bash
curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8089/internal/api/users?search=john&page=0&size=20'
```

**`GET /internal/api/users`** — `PageResponse<UserSummary>`,
`?search=` matches email/phone across all orgs.

UserSummary fields: `id`, `email`, `phone`, `orgId`, `orgName`, `role`
(`OWNER`/`MANAGER`/`WORKER`/`ADMIN`/`STAFF`, uppercase), `trialUsed`,
`status` (`"active"`), `createdAt`,

- `planName`, `planStatus` — the user's **org's live** subscription plan/status.
  Same live-lifecycle rule as the subscription view: only
  `TRIALING`/`ACTIVE`/`PAST_DUE` count; terminal/absent rows read `NONE` and the
  three plan-adjacent fields are omitted entirely.
- `planExpiryDate` — that subscription's `endDate` (`LocalDate`, string
  `YYYY-MM-DD`), omitted when `NONE`.
- `trialDaysRemaining` — only when `planStatus === 'TRIALING'`.

**`GET /internal/api/users/{id}/org`** — the click-through:

```json
{ "userId": "…", "org": { …same shape as GET /internal/api/orgs/{id} } }
```

For an **org-less** user this returns `org: null` (**200**, not 404) — the UI
renders "no org yet". The totals block is the org's own stats embedded on the
same detail body.

### Subscriptions (append-only history)

```bash
curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8089/internal/api/subscriptions?orgId=<uuid>&page=0&size=20'
```

`?orgId=` (optional) and `?status=` (optional; any of `TRIALING, PENDING,
ACTIVE, PAST_DUE, SUSPENDED, CANCELLED, EXPIRED` — exact uppercase names plus
case-insensitive query binding) filter; **terminal rows are included** — this is
history, not a live-only view. Each row carries an embedded `plan` block
(see below) **without pricing** — pricing belongs to a live Razorpay fetch and
never appears on subscription reads.
Rows are newest-first.

Subscription shape: `id`, `orgId`, `status`, `startDate`, `endDate`
(both `YYYY-MM-DD` strings, UTC days), `daysRemaining` (long ≥ 0, omitted when no
`endDate`), `billingCycle` (`MONTHLY`/`YEARLY`, omitted while trialing), and
`plan` — `{ id, name, isTrial, maxUsers, maxCars, reportWindowMonths,
statsRangeYears, invoiceGeneration }` where a **null number means unlimited**
(and the key is omitted by `non_null`).

### Payments & payment events

```bash
curl -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8089/internal/api/payments?orgId=<uuid>&page=0&size=20'
```

Rows come from `razorpay_payments` (money the main API recorded — no live
Razorpay fetch here). Payment shape:

| Field | Notes |
|---|---|
| `id`, `subscriptionId` | |
| `razorpayPaymentId`, `razorpayOrderId`, `razorpayInvoiceId` | |
| `amount` | **rupee decimals** already — the paise source was converted ("1990.00") |
| `currency` | |
| `status`, `paidAt` | `paidAt` may be null until the charge clears |

`GET /internal/api/payments/events?orgId=…` — webhook event audit listing:
`id`, `razorpayEventId`, `eventType`, `processingStatus`
(`PROCESSED`/`IGNORED`/`FAILED`), `receivedAt`, `processedAt`, `errorMessage`
- raw payloads are intentionally not exposed on the list (big / rarely needed).

Timestamps on payments/events render in the **filtered org's** timezone when an
`?orgId=` is given; on the unfiltered page they render UTC (there is no single
org zone). Anonymous timeline renders UTC — fine for money-audit listings.

### Whitelist (the console's only writes)

```bash
# List (newest first)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8089/internal/api/allowed-emails

# Add
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":"dev@mygarageone.com"}' \
  http://localhost:8089/internal/api/allowed-emails

# Remove
curl -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8089/internal/api/allowed-emails/<row id>
```

Response shape: `{ id, email, createdByEmail, createdAt }` (list returns an
array — no pagination, the list is small).

Rules (settled, do not soften the UI around them):

- `POST` normalizes to lowercase and rejects with **409** `code:
  email_already_whitelisted` if already present. The caller's own token email is
  recorded in `createdByEmail` (audit trail).
- `DELETE` → **409** `code: cannot_remove_self` when the id is the caller's own
  row — a caller must stay logged in and whitelisted; the UI should not offer a
  delete button on your own row (or should expect the 409 and explain).
- The list is flat — no roles among console users in v1; any whitelisted user
  may add/remove another.
- Exactly **one seeded row** pre-exists: `radhaiya.solutions@gmail.com`
  (never delete it, or nobody can ever log in again).

---

## 3. Pagination & query caps

Every list endpoint uses the same envelope — **never** a raw Spring `Page`:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 37,
  "totalPages": 2,
  "last": false
}
```

- Defaults: `size=20` per endpoint; `?size=` larger than **50** is silently
  clamped to 50 (Spring's global cap, never an error).
- `page` is 0-indexed.
- The whitelist list endpoint is an exception — plain array, no envelope.

---

## 4. Timestamps & money

- All timestamps are UTC in the database; console responses render them as a
  **plain wall-time ISO string** (no offset): `2026-09-24T21:43:00`.
- Per-row rendering: org/user views convert to the **subject org's** timezone
  (`organizations.timezone`); rows without an org (or cross-org unfiltered
  lists, whitelist rows, console rows) render **UTC**. Treat the string
  as display-only, never parse it back as a ZonedDateTime.
- `LocalDate` (plan `startDate`/`endDate`, `planExpiryDate`) carries **no
  zone** — UTC day boundaries.
- Razorpay `amount` is already converted paise→rupee decimals
  ("4990.00"); `currency` is the original 4217 code.

---

## 5. Errors (RFC 7807 `application/problem+json`)

| Status | When | `code` property |
|---|---|---|
| 400 | validation (body) — includes `errors` map `{field: message}` | — |
| 401 | missing/invalid **internal** token; refresh/replay failures | — |
| 403 | login email not on the whitelist | (code is in the `detail`: `email_not_whitelisted`) |
| 404 | org / user / subscription-chain entity id unknown (from the console's global view) | — |
| 409 | whitelist conflicts (coded — branch on `code`) | `email_already_whitelisted`, `cannot_remove_self` |

The console reuses the same exception handler as the tenant API — same shape:
`{ type, title, status, detail, code? }`.

For machine-readable branching: **409s always carry `code`;** the 403 login
gate's code sits in the `detail` field (`email_not_whitelisted`) — key your
sign-in-screen branch on `problem.detail.includes('email_not_whitelisted')`.

---

## 6. Quick reference — auth order of operations

1. Firebase JS SDK sign-in → `idToken`
2. `POST /internal/api/auth/firebase` → store `{token, refreshToken}`
3. Attach `Authorization: Bearer <token>` on every `/internal/api/**` call
4. On 401 → `POST /internal/api/auth/refresh` (single-use rotation)
   - 403 `email_not_whitelisted` → signed out (email was removed mid-session)
   - 401 → back to Firebase re-auth
5. "Sign out" → `POST /internal/api/auth/logout` with the current refresh token

The seed whitelist row (added by the migration): **`radhaiya.solutions@gmail.com`**
— the first console user; only that user can bootstrap everyone else via
`POST /internal/api/allowed-emails`.
