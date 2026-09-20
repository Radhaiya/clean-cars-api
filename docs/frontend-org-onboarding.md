# Frontend: org-less users & trial onboarding

**Change:** a user now signs up **without an organization**. The org is created only
when they start a trial (later: buy a plan). Until then their token has no `org_id`
and every org-scoped endpoint returns `403`.

Users who already have an org are **unaffected** — their tokens still carry `org_id`,
everything works as before. This only affects the sign-up → first-org window.

---

## The org-less state

After `POST /api/auth/firebase` (sign in with Google / Apple / phone OTP via Firebase Auth), a new user's access token (JWT) has:

- **no `org_id` claim**
- `role: "staff"` (placeholder — not a real permission yet)

Any endpoint that needs a tenant returns **`403`** for this user: `/api/customers`,
`/api/cars`, `/api/employees`, `/api/vendors`, `/api/service-catalog`,
`/api/service-categories`, `/api/service-orders`, `/api/organization`, …

---

## What works without an org

| Endpoint | Behaviour for an org-less user |
|---|---|
| `POST /api/auth/firebase` · `/refresh` · `/logout` | normal |
| `GET /api/me` | returns the user; `orgId` / `orgName` / `plan` are **null / absent** |
| `GET /api/plans` | the pricing catalogue (unchanged) |
| `GET /api/subscription` | **always `200`** → `{ "active": false, "onTrial": false, "status": "NONE" }` |
| `POST /api/subscription/trial` | creates the org + starts the trial (below) |

---

## Onboarding gate

After login, determine **"has an org?"** — any of:

- `GET /api/me` → `orgId` is present, **or**
- decode the JWT and check for an `org_id` claim, **or**
- `GET /api/subscription` → `status !== "NONE"`

Then:

- **No org** → route to the plan-picker / "start your trial" screen.
  Do **not** render the dashboard or pre-call any org endpoint.
- A `403` from an org endpoint while the user has no org means **"go to onboarding"**,
  not "session expired / log out". Handle it as a redirect, not a logout.

```
login ─▶ GET /api/me
           │
   orgId present? ──yes──▶ dashboard
           │
           no
           ▼
   plan picker  ─▶  POST /api/subscription/trial  ─▶  swap token  ─▶  dashboard
```

---

## Start a trial (and the token swap)

### Request

`POST /api/subscription/trial`

```json
{
  "orgName": "Free Motors",
  "timezone": "Asia/Kolkata",
  "contactPhone": "9998887777",
  "contactEmail": "owner@freemotors.in",
  "address": "MG Road, Pune"
}
```

- `orgName` and `timezone` are **required**; `contactPhone` / `contactEmail` / `address` are optional. `timezone` is the garage's IANA zone id (e.g. `Asia/Kolkata`, `America/New_York`) — the backend rejects unknown values with a 400. All API timestamps come back in this timezone (stored UTC internally).
- **No `planId`** — there's exactly one Trial plan, and the backend always uses it. It's not a discount on Starter/Workshop/Pro; it's its own catalogue row with all features unlocked (like Enterprise) but reduced limits, free, one-time per account. You can still show it on the pricing page — `GET /api/plans` returns it alongside the others with `isTrial: true`.

### Response `201`

```json
{
  "subscription": {
    "id": 2,
    "planId": 5,
    "planName": "Trial",
    "status": "TRIALING",
    "startDate": "2026-09-10",
    "endDate": "2026-09-24",
    "trialDays": 14,
    "daysRemaining": 14
  },
  "token": "<fresh access token — now carries org_id + role: OWNER>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### ⚠️ You must swap the access token

The token the user logged in with has **no `org_id`**. `response.token` is a fresh
access token that does. On success:

1. **Replace the stored access token** with `response.token`.
2. **Keep the existing refresh token** — it does not change and stays valid.
3. From here on, org-scoped endpoints work.

(If you skip the swap, the next `POST /api/auth/refresh` would also mint a token with
`org_id` now — but the trial response already hands you one, so there's no need.)

---

## Trial error handling

RFC-7807 `ProblemDetail` bodies. Branch on `status` + `code`:

| Status / `code` | Meaning | Suggested UI |
|---|---|---|
| `400` + `errors` map | missing / invalid `orgName` or `timezone` | inline field errors |
| `409` `user_already_has_org` | user already onboarded | send to dashboard |
| `409` `trial_already_used` | this account already used its one free trial | show paid plans (paid flow TBD) |

---

## Also

- **1 user : 1 org.** No org switcher, no multi-org UI. A second org = a second account.
- After onboarding, the user's `role` is `OWNER` (in the new token and in `GET /api/me`).
- `GET /api/subscription` is the source of truth for "what plan am I on / how many trial
  days left" — poll it on the dashboard; `daysRemaining` counts down to `endDate`.
