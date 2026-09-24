# Org invites — employees are the seat (docs/FEATURE-INVITES.md)

**Source of truth for everything invite-related. Touch this file when working on invites, the manager/worker role model, the employee roster/quota, leaving an org, or the `isManaged` flag.**

Feature summary: the plan's `max_users` caps the org's **`employees` roster** — that is the USP ("assign n workers in the module"). The **owner** picks one of his employee rows (whose `email` he's filled in), invites it with role `manager` or `worker`. No email transport exists — the invitee, after signing in (Firebase), sees pending invites **in-app** (`GET /api/invites/me`) and **accepts** (joins the org with the invited role; `employees.user_id` links the account to that roster row) or **declines**. A member (manager/worker, not owner) can **leave** voluntarily; deleting the employee row clears everything the person held (jobs, invite, link). `GET /api/me` exposes `isManaged`, `GET /api/employees` is now the team screen.

## 1. Decisions & rules (settled — do not silently change)

| Rule | Decision |
| --- | --- |
| Who can invite | **Owner only** (`AuthContext.require(UserRole.OWNER)`); managers/workers get 403 |
| What an invite targets | Exactly one `employees` row of the caller's org (`InviteRequest {employeeId, role}`); the email comes **from that row** — an employee without an email cannot be invited (400) |
| The seat model | Seats = `employees` rows. `maxUsers` caps the roster at **create** (`EmployeeCreateService` → 409 `user_limit_reached`) and again at invite/accept. **The owner is NOT an employee row and consumes no seat.** Managers are employees too (1 invite : 1 roster row) |
| Where the email lives | On the employee row (`employees.email`, `UNIQUE(org_id, email)`), optional until invitation time; stored lowercase |
| Employee ⇄ user link | `employees.user_id` — set by the accepted invite (unique FK → `users.id`). An email whose `users` row is in ANY org is `user_already_in_org` (409) |
| Duplicate invites | One pending invite per (org, **employee**) — `invite_already_pending`; plus one pending per (org, email). Declined/expired/revoked are re-invitable |
| Who cannot be invited | A person whose `users` row is in **any** org (not just this one) — 1 user : 1 org |
| Linked already | Employee already has a link → `employee_already_linked` (send + accept) |
| Expiry | `expires_at = now + 7 days` (`InviteService.INVITE_TTL_DAYS`); expired invites hidden from `GET /api/invites/me`, rejected on accept |
| Decline vs Revoke | Decline (invitee) → `declined`; revoke (owner, pending only) → `revoked`. Both leave the seat open, the employee re-invitable |
| Live-plan gate | Creating employees AND inviting/accepting require a live subscription (trial counts) → `org_no_live_subscription` (409) |
| Leaving | `POST /api/org/leave` — owner cannot (`owner_cannot_leave`); members set `users.org_id = null`. **The `employees` row and its link survive leave** (roster entry remains, re-invitable to the same slot). Call `GET /api/me` after; claims of old tokens are stale until expiry |
| Deleting an employee | **Never a 409** — one transaction: job cards lose the assignee (`service_orders.employee_id = NULL`, jobs kept), invite rows of that employee deleted ("invitation cleared")… the seat drops to free, the linked account is unlinked exactly like leave. History keeps the job, loses the assignee name (joined lookup) |
| `isManaged` | `true` iff the user is in an org and is **not** its owner (joined via invite) — `User.isManagedMember()`; always serialized |
| Email casing | All invite/employee emails normalized lowercase; comparisons case-insensitive (`findByEmailIgnoreCase`) |
| Legacy `ADMIN`/`STAFF` | Still valid enum values until revisited; nothing assigns them anymore except the pre-004 `provisionFromFirebase` default (`STAFF`) — revisit later |
| Migrations | `004-invite-roles.sql` (role enums + `declined` status), `005-employee-user-link.sql` (employees.email/user_id + UNIQUE(org,email), org_invites.employee_id) |

## 2. API

All `/api/invites/**`, `/api/employees/**` + `POST /api/org/leave` are authenticated (Bearer). Invite send/list/revoke and role-based screens require `owner`.

### Sender side (owner only)

| Endpoint | Method | Body | Returns | Errors |
| --- | --- | --- | --- | --- |
| `/api/invites` | POST | `{ employeeId, role: "manager\|worker" }` → 201 | `InviteResponse` | 400 employee w/o email; 409 `user_already_in_org`, `invite_already_pending`, `employee_already_linked`, `user_limit_reached`, `org_no_live_subscription`, `invite_invalid_role`; 404 employee |
| `/api/invites` | GET | → 200 | all invites ever sent, newest first (history incl. terminal) | — |
| `/api/invites/{id}` | DELETE | → 204 | — | 404 invite, 409 `invite_not_pending` |

### Employee roster (owner + managers read; create/update/delete owner-managed as before)

| Endpoint | Notes |
| --- | --- |
| `POST /api/employees` | `{ name, email? }`. **Plan seat enforced here** — a full roster → `user_limit_reached`; no live plan → `org_no_live_subscription`; duplicate email in org → `employee_email_exists` (409) |
| `PUT /api/employees/{id}` | Same validation; own email excluded from the duplicate check |
| `GET /api/employees` | Paged; each row: `{ id, name, email, user?: {id,name,email,role}, createdAt }` — `user` is the **linked account** (the signed-in view of that seat). Plus the org's invite log via `GET /api/invites` (invites carry `employeeId`, so the UI can render per-employee status chips) |
| `DELETE /api/employees/{id}` | → 204 always; cascade per §1 (assignments cleared, invite rows deleted, linked account org-less) |

### Invitee side (any authenticated user)

| Endpoint | Method | Returns | Errors |
| --- | --- | --- | --- |
| `/api/invites/me` | GET | 200 pending+unexpired invites, with `orgName`, `invitedByName`, `employeeName` | — |
| `/api/invites/{id}/accept` | POST | 200 `{ token, tokenType: "Bearer", expiresIn }` — **fresh token** (old token lacks `org_id`/new role); the refresh token stays valid | 409 `user_already_in_org`, `invite_email_mismatch`, `invite_not_pending`, `invite_expired`, `employee_already_linked`, `user_limit_reached`, `org_no_live_subscription`, `invite_no_email`; 404 invite |
| `/api/invites/{id}/decline` | POST | 204 | 409 `invite_email_mismatch`, `invite_not_pending`; 404 invite |

### Leaving

| Endpoint | Method | Returns | Errors |
| --- | --- | --- | --- |
| `/api/org/leave` | POST | 204 | 409 `owner_cannot_leave`, `user_not_in_org` |

### `GET /api/me` — unchanged since 004

`isManaged` (boolean) = true when the account joined via an invite it accepted (`orgId != null && role != OWNER`). `role`: `OWNER | MANAGER | WORKER` (+ ADMIN/STAFF legacy).

### `InviteResponse`

```jsonc
{ "id": "...", "email": "invited@x.com", "role": "WORKER", "status": "PENDING",
  "employeeId": "...", "employeeName": "Ravi",
  "orgName": "Acme Motors",       // populated on GET /api/invites/me (+ on send)
  "invitedByName": "Owner name",  // populated on send + GET /api/invites/me
  "createdAt": "...", "expiresAt": "...", "acceptedAt": null }
```

Statuses: `PENDING ACCEPTED EXPIRED REVOKED DECLINED`. Errors are RFC-7807 `ProblemDetail` with a machine-readable `code`.

## 3. UI instructions

**Driving state: `GET /api/me`** — owner surfaces when `role === 'OWNER'`; `isManaged === true` → member workspace (hide invites, org settings, subscription screens — members cannot renew/change plan, create employees, or delete others).

- **Seats gate (owner)**: show "Add employee" active only when `plan != null && (plan.maxUsers == null || currentUsers < plan.maxUsers)` — `currentUsers` is really "linked roster size" under this model; on `user_limit_reached` show upgrade CTA; on `org_no_live_subscription` route to the billing flow.
- **Employee form (owner)**: name + optional email (`POST/PUT /api/employees`). `employee_email_exists` → inline "another worker already uses that email".
- **Invitation screen (owner)**: `POST /api/invites {employeeId, role}` — employee dropdown (prefill from the employee being viewed), role picker **Manager / Worker** only. Error mapping: `employee_already_linked` ("Ravi already has an account"), `user_already_in_org` ("that person already belongs to an organization"), `invite_already_pending` ("invite already pending"), `invite_invalid_role` never shows (picker restricted).
- **Team screen (owner/manager)**: render `GET /api/employees`: each row name, email, account info from `user` (linked = signed in), plus status chips from `GET /api/invites` joined on `invite.employeeId`: PENDING (show Revoke on unexpired pending), DECLINED/EXPIRED (show "Re-invite"), REVOKED, ACCEPTED, or linked-account. If the employee row has **no email**, show "Add email to invite" affordance instead of an invite button (API 400s otherwise).
- **Delete worker confirmation** (owner): warn that deletion **clears his invite, unlinks his login account, and removes him from every job card he was assigned to** (jobs themselves are kept). Backend always succeeds (no 409 path).
- **Invite inbox (invitee, `isManaged === false && orgId == null`)**: after sign-in call `GET /api/invites/me` — cards with `orgName`, `invitedByName`, granted role, `employeeName`, `expiresAt`; **Accept**/**Decline**. Accept → swap stored token for the returned one immediately and re-`GET /api/me` (now `isManaged: true`). Expired is already filtered server-side; on other errors show the message ("ask the owner to re-send").
- **Leave (`isManaged === true`)**: settings → "Leave organization"+confirm ("you lose access; re-joining needs a new invite"). On 204 re-`GET /api/me` and route to the org-less landing. Hide entirely for `role === 'OWNER'` (backend still answers `owner_cannot_leave`).
- **Token reminder**: claims freeze at issue time; after accept use the returned token, after leave/any membership change re-`GET /api/me` for authoritative state.

## 4. Code map (fast traversal)

| Concern | Files |
| --- | --- |
| Migration 004 (roles / invite status) + 005 (email, user_id, employee_id) | `db/changelog/migrations/004-invite-roles.sql`, `db/changelog/migrations/005-employee-user-link.sql` |
| Roles enum | `entity/UserRole.java` (MANAGER/WORKER, `isInvitable`; ADMIN/STAFF legacy), `entity/UserRoleConverter.java` |
| Invite status | `entity/InviteStatus.java`, `entity/InviteStatusConverter.java` |
| Entities | `entity/Employee.java` (email, userId, `linkUser`), `entity/OrgInvite.java` (employeeId; `revoke`/`decline`/`accept`, `isPending`, `isExpired`), `entity/User.java` (`acceptInvite`, `leaveOrg`, `isManagedMember`) |
| Repos | `repository/OrgInviteRepository.java` (org/email/**employee** finders), `repository/EmployeeRepository.java` (`findByOrgIdAndEmailIgnoreCase`, `findByIdForUpdate` row-lock, `countByOrgId` = seats), `repository/UserRepository.java` (`findByEmailIgnoreCase`, `findByOrgId`, `countByOrgId`), `repository/ServiceOrderRepository.java` (`clearEmployeeAssignments`) |
| Invite service | `service/InviteService.java` (workflow class; seats = employees; INVITE_TTL_DAYS = 7) |
| Seat enforcement | `service/EmployeeCreateService.java` (quota + duplicate-email + live-plan gate), `service/EmployeeUpdateService.java` (duplicate email, own row excluded) |
| Employee delete cascade | `service/EmployeeDeleteService.java` (orders → invites → unlink → row) |
| Reads | `service/EmployeeReadService.java` (joins linked users → `user` block) |
| Leave | `service/UserService.java` (`leaveOrg`), `controller/UserController.java` (`POST /api/org/leave`) |
| Controllers | `controller/EmployeeController.java` (unchanged surface), `controller/InviteController.java` |
| DTOs | `dto/EmployeeRequest.java` (+email), `dto/EmployeeResponse.java` (+`UserBrief`), `dto/InviteRequest.java` (`employeeId`), `dto/InviteResponse.java` (+employeeId/Name), `dto/AcceptInviteResponse.java`, `dto/UserProfile.java` (`isManaged`) |
| Errors | `exception/ConflictException.java` (`employee_*`, `invite_*`, `user_*`, `owner_cannot_leave`, `org_no_live_subscription` codes) |
| Token minting | `service/JwtService.java` `issueToken(user)` — unchanged claims |
| Schema design doc | `src/main/resources/cleancars_schema (1).dbml` (user_role / invite_status enums, employees, org_invites) |
| Security | No changes — routes fall under `anyRequest().authenticated()`; role gate is `AuthContext.require(UserRole.OWNER)` inside `InviteService`/create+list+revoke |

## 5. Edge cases handled

- Same person under two employee rows → the second invite pre-check fails (`user_already_in_org` because the user exists; then `employee_already_linked` for the second row after accept).
- Employee deleted between send and accept → accept 404s on the employee (`employee_id` no longer matches an org row).
- Seat taken between send and accept by **another person** (re-invite race) → `employee_already_linked` on accept.
- Quota race (employees added/edited between send and accept) → accept re-checks seats.
- Owner invites himself → his `users` row carries the org → `user_already_in_org`.
- Phone-only accounts (no email): never inviteable, `GET /api/invites/me` empty, accept throws `invite_no_email`.
- Concurrent accept/leave on one account → serialized by `PESSIMISTIC_WRITE` on the user row (accept also locks the employee row).
- Two employees with the same email in one org → prevented by `UNIQUE(org_id, email)` + coded 409 at create/update.
- Leave vs delete dedupe: both idempotent to "already org-less" (`user_not_in_org` / no-op path).

## 6. Not built (explicit non-goals for now)

- Real email transport (no `JavaMailSender`/provider; `token` column reserved for future magic links).
- Changing a linked member's role (re-invite after leave is the flow).
- Legacy `ADMIN`/`STAFF` removal from the enum ("revisit later").
- Owner offboarding (delete org) — separate feature.
- Re-assigning deleted employees' job cards to someone else (assignee set to NULL per spec).
- `invitedByName` on the org's own invite list (interior data; add if the UI asks).
