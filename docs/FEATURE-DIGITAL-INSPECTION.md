# Digital Vehicle Inspection (DVI) — API spec

> Status: **PROPOSAL, no code yet.** UI counterpart: the UI repo's `docs/features/digital-inspection.md`.
> Migration: `019-digital-inspection.sql`.

## 1. What it does

A staff member opens a service order and adds a **Digital Inspection**: photos / short videos (each with a name such as
"Left rear wheel" and a note), a fixed **checklist** (Brakes, Tires, Headlights … each green / yellow / red with a note) and a summary note.
They then create a **24-hour link** and send it to the customer through the phone's share sheet. The customer opens it **without logging in**, sees the
media, notes, checklist and the order's services **with prices**, answers **Yes / No per service**, and submits **once**.

Decisions already made:

| Topic | Decision |
|---|---|
| A "No" answer | **Flags** the line for staff. It never deletes or edits the order line (that would change the bill silently). |
| Prices on the customer page | Shown, per line and total, plus a live "approved total" on the page. |
| Media retention | Photos / videos deleted **48 h after the inspection is created**. Checklist, notes and customer answers are **kept**. |
| Storage | **MinIO** (S3 API), run in Docker, prod-grade config. Code talks S3, so prod can later point at AWS S3 unchanged. |
| Video | Included, max 30 s. |
| Link | 24 h, single submit, token only (no extra PIN). |
| Security | High: strict upload validation, re-encoding, private bucket, hashed tokens, rate limits, hardened headers (see §6). |

Out of scope for v1 (listed in §10): push notification to staff, server-side WhatsApp/SMS send, ClamAV, video transcoding, PDF export.

## 2. Architecture overview

```
 Expo app (staff, JWT)                       Customer browser (no auth)
        │  multipart upload                          │  GET/POST /public/inspections/{token}
        ▼                                            ▼
 ┌────────────────────────── clean-cars-api ───────────────────────────┐
 │ /api/**  tenant chain (JWT)          /public/**  permit-all chain    │
 │  Inspection*Service                   PublicInspectionService        │
 │  MediaValidator ─► MediaStorage (S3 client) ◄── media streamed       │
 │                         │                       through the API       │
 │  InspectionMediaSweeper (job)                                         │
 └─────────────────────────┼────────────────────────────────────────────┘
                           ▼  internal network only, never published
                         MinIO  (private bucket, lifecycle backstop)
```

Key points:

- **MinIO is never exposed to the internet and never hands out presigned URLs.** The API streams media to both staff and customers
  (with `Range` support for video). Access control stays in one place, and there is no URL that can be forwarded around.
- **Uploads go through the API**, not straight to the bucket, so every byte is validated before it is stored (§6.1). A presigned PUT would let a
  client store anything.
- The customer page is a small **static page served by this API** (§7), so there is no new deployable and no CORS.

Follows `docs/ARCHITECTURE.md`: one service per operation, org-scoped finders, DTO records, `ConflictException` factories with codes.

## 3. Data model (migration 019)

All tables carry `org_id` and every repository finder is org-scoped. Ids are `BINARY(16)`, timestamps UTC, like the rest of the schema.

### `inspections` — one per service order
| Column | Notes |
|---|---|
| `id`, `org_id`, `service_order_id` | `UNIQUE (service_order_id)` — one inspection per order |
| `vehicle_type` | `CAR` / `BIKE`, copied from the order; picks the checklist template |
| `template_version` | int, so the fixed list can evolve without breaking old inspections |
| `status` | `DRAFT` → `SENT` → `RESPONDED`; `EXPIRED` derived on read when the active link lapsed unanswered (not stored) |
| `summary_note` | TEXT, ≤ 2000 chars |
| `media_expires_at` | `created_at + 48h` |
| `created_by`, `created_at`, `updated_at` | |

### `inspection_items` — the checklist
`id, inspection_id, item_key VARCHAR(40), position, status ENUM('GREEN','YELLOW','RED','NOT_CHECKED'), note VARCHAR(500)`.
`UNIQUE (inspection_id, item_key)`. Rows are created from the template when the inspection is created, all `NOT_CHECKED`.
`NOT_CHECKED` rows are not shown to the customer.

### `inspection_media`
`id, inspection_id, object_key, kind ENUM('IMAGE','VIDEO'), content_type, size_bytes, width, height, duration_ms, label VARCHAR(80), note VARCHAR(500), position, created_at, deleted_at NULL`.
- `object_key` = `org/{orgId}/inspection/{inspectionId}/{random-uuid}.{jpg|mp4}` — never derived from the uploaded file name.
- After the sweeper runs: `object_key` NULL and `deleted_at` set; label / note stay (so the report still reads sensibly: "Left rear wheel — photo no longer available").

### `inspection_shares` — the customer link
`id, inspection_id, token_hash CHAR(64) UNIQUE, expires_at, revoked_at NULL, items_snapshot JSON, order_total DECIMAL(12,2), first_viewed_at, last_viewed_at, view_count, responded_at, responded_ip_hash, responded_user_agent VARCHAR(255), created_by, created_at`.
- Only the **SHA-256 of the token** is stored. The raw token exists only in the create-link response (§4), so a database leak does not leak working links.
- `items_snapshot`: the order's service lines frozen at link-creation time (id, name, qty, unit gross, line gross, tax text). The customer approves exactly what they saw even if staff edit the order afterwards.
- At most one **active** share per inspection: creating a new link revokes the previous one.

### `inspection_decisions` — customer answers (kept after media is gone)
`id, inspection_id, share_id, service_order_item_id, service_name, line_gross, decision ENUM('APPROVED','DECLINED'), decided_at`.
`service_order_item_id` is **not** a hard FK (the line can later be deleted by staff); the name / price snapshot keeps the record readable.

## 4. Staff endpoints (tenant JWT, `/api/**`)

Maintenance mode and org scoping apply as usual.

| Method / path | Purpose |
|---|---|
| `GET  /api/service-orders/{orderId}/inspection` | The inspection (with a `usage` block: `{ images, videos, totalBytes, limits{…} }` so the app can show "46 of 120 MB used") with checklist, media list, share state and decisions. `204` if none exists. |
| `POST /api/service-orders/{orderId}/inspection` | Create (checklist rows seeded from the template). `201`; `409 inspection_exists` if one exists; `409 order_cancelled` for a cancelled order. |
| `PUT  /api/inspections/{id}` | Save `summaryNote` + checklist (`[{itemKey, status, note}]`). Unknown keys → 400. Refused after `RESPONDED` (`409 inspection_locked`). |
| `POST /api/inspections/{id}/media` | `multipart/form-data`: `file`, `label`, `note`. Validated + re-encoded (§6.1). `201` with the media row. |
| `PATCH /api/inspections/{id}/media/{mediaId}` | Change `label`, `note`, `position`. |
| `DELETE /api/inspections/{id}/media/{mediaId}` | Delete the row and the object. |
| `GET  /api/inspections/{id}/media/{mediaId}/content` | Stream the file to staff (JWT required, `Range` supported). |
| `POST /api/inspections/{id}/share` | Create a link: snapshots the order lines, revokes any active share, returns `{ url, expiresAt }`. **The only time the raw token is returned.** `409 inspection_empty` if there is no media and no checked item. |
| `DELETE /api/inspections/{id}/share` | Revoke the active link now. |
| `DELETE /api/inspections/{id}` | Delete inspection + objects (also triggered when its service order is deleted — hook into `ServiceOrderDeleteService`). |

The detail response also carries, for the order screen: `share: { status: ACTIVE|EXPIRED|REVOKED|RESPONDED, expiresAt, viewCount, lastViewedAt, respondedAt }` and
`decisions[]`. `ServiceOrderResponse` is **not** changed; instead the order list/detail add one small field `inspection: { id, status } | null` so the UI can render the entry button state without a second call.

### Link format
`{app.inspection.public-base-url}/i/{token}` where `token` = 32 random bytes (`SecureRandom`) base64url → 43 chars, 256 bits.

### Services (one per operation, per ARCHITECTURE.md)
`InspectionCreateService`, `InspectionReadService`, `InspectionUpdateService`, `InspectionDeleteService`,
`InspectionMediaService` (upload / patch / delete / stream), `InspectionShareService` (create / revoke),
`PublicInspectionService` (view + respond), `InspectionTemplates` (fixed lists), `MediaValidator`, `MediaStorage` (interface) + `S3MediaStorage`,
`InspectionMediaSweeper` (scheduled job), `InspectionRateLimiter`.

## 5. Public endpoints (no auth, `/public/**`)

Added to the tenant chain's permit-all list as `/public/inspections/**` (never under `/api`, so the maintenance interceptor is not involved and
the tenant JWT filter is never consulted).

| Method / path | Purpose |
|---|---|
| `GET  /public/inspections/{token}` | The report: org name, vehicle label + plate, customer first name, checklist (checked items only), media list (id, kind, label, note, `available`), summary note, services snapshot with prices + total, link `expiresAt`, and `state`. |
| `GET  /public/inspections/{token}/media/{mediaId}` | Streams the file (images / video, `Range` supported). |
| `POST /public/inspections/{token}/response` | Body `{ decisions: [{ itemId, approved }] }` — **all** snapshot items required. Single use. Returns the final state + approved total. |

`state`: `OPEN`, `RESPONDED`, `EXPIRED`, `REVOKED`. An unknown token is a plain `404` with an empty problem body. An expired / revoked link is `410`
(`code: link_expired | link_revoked`) with no report data. A second submit is `409 already_responded` (the lost-update race is closed with a
conditional `UPDATE … WHERE responded_at IS NULL` so two concurrent submits cannot both win).

What the public response **must not** contain: customer phone / email / address, org id, user ids, internal ids other than the snapshot line ids and media ids,
employee names, payment data, odometer, notes on the order itself. Build the DTO explicitly (allow-list), never serialise entities.

On submit (one transaction): validate the answer set equals the snapshot item set → insert `inspection_decisions` → set share `responded_at` / IP hash / UA →
set inspection `RESPONDED`. No change to `service_orders` or `service_order_items`. Staff see the flag through `decisions[]` on the inspection and a
declined-count on the order (`inspection.declinedCount`).

If the snapshot has no lines (e.g. an AMC redemption), the page is a read-only report with no submit button, and the share is simply open until expiry.

## 6. Security (the part to review hardest)

### 6.1 Upload validation (staff uploads, but treated as hostile input)
Config under `app.inspection.upload.*`. Defaults:

| Rule | Value |
|---|---|
| **Per-file size** | image **≤ 10 MB** as uploaded, video **≤ 50 MB** (checked on `Content-Length` first, then again on the bytes actually read) |
| Request cap (`spring.servlet.multipart.max-file-size` / `max-request-size`) | 55 MB, so oversized bodies are rejected before the app reads them |
| Allowed images | JPEG, PNG, WebP (the app converts HEIC to JPEG on the phone) |
| Allowed videos | MP4 / MOV (`ftyp` box with brands `isom, mp41, mp42, qt`) |
| **Per inspection, count** | ≤ 20 images, ≤ 3 videos |
| **Per inspection, total size** | **≤ 120 MB** of stored bytes (sum of `size_bytes` of live media: re-encoded size for images, as-received for videos). An upload that would push the sum over the limit is refused with `400 invalid_media` / `inspection_size_limit` and the response says how many MB remain |
| Per org rate | ≤ 60 uploads / hour |
| Duration | videos ≤ 30 s (+ 1 s tolerance), read from the `mvhd` box |
| Dimensions | images ≤ 12,000 × 12,000 px and ≤ 40 MP (decompression-bomb guard) |
| Label / note | plain text, trimmed, control characters stripped, ≤ 80 / 500 chars; stored raw, **escaped only at output** |

Validation order, all server-side, fail → `400 invalid_media` with a safe code (`unsupported_type`, `too_large`, `too_long`, `corrupt`, `limit_reached`, `inspection_size_limit`):

0. **Limits are checked under a row lock** on the `inspections` row (`SELECT … FOR UPDATE`) together with the count / size sums, so two parallel uploads
   cannot both squeeze under the cap. The quota check runs before the bytes are stored and again with the final stored size after re-encoding.
1. **Ignore the client's `Content-Type` and file name.** Detect the type from **magic bytes** (JPEG `FF D8 FF`, PNG signature, WebP `RIFF…WEBP`, MP4 `ftyp`).
2. Images are **fully decoded and re-encoded** (ImageIO → new JPEG, quality ~82, longest side ≤ 2048 px). Re-encoding discards EXIF (**GPS location**, device
   info), embedded payloads and polyglot tricks (a file that is both a valid image and a script/archive). The stored object is always our own JPEG.
3. Videos: container parse for `ftyp` + `mvhd` (duration, timescale sanity), reject if the box structure is malformed or the declared sizes exceed the file.
   Videos are stored as received (no transcoding in v1) — see §10 for the optional ffmpeg stage that would also strip metadata.
4. Object key is generated server-side (random UUID); the user's file name is discarded and never reaches the file system or a header.
5. Stream to a temp file with a hard byte cap (never buffer unbounded in memory), then upload to MinIO, then delete the temp file in `finally`.
6. Optional hook for an antivirus scan (`app.inspection.upload.scan.enabled=false`, ClamAV `INSTREAM`) — interface present, implementation deferred.

### 6.2 Serving media
- Always `Content-Type` from **our stored value** (`image/jpeg` / `video/mp4`), `X-Content-Type-Options: nosniff`,
  `Content-Disposition: inline; filename="media"` (fixed name), `Cache-Control: private, no-store` (public view) — customer media must not sit in shared caches.
- Staff stream endpoint checks `org_id` through the inspection; the public stream endpoint checks the token, share validity **and** that the media belongs to
  that share's inspection and is not deleted / past `media_expires_at`.
- Past `media_expires_at` the API answers `410` for the file even if the sweeper has not run yet. The 48 h promise is enforced at read time, not just by the job.

### 6.3 Tokens and links
- 256-bit random, only the SHA-256 stored; lookup by hash. Comparison is on the hash, so there is no timing signal about the raw token.
- 24 h expiry, `revoked_at`, single successful submit. Link creation requires staff auth and is audited (`created_by`).
- The token is never logged: access-log / error-handler configuration redacts `/public/inspections/*` path segments (log the route template).
- Tokens only travel in the path of an HTTPS URL. The page sets `Referrer-Policy: no-referrer` so an outbound link can never leak it.

### 6.4 Abuse controls on the unauthenticated surface
`InspectionRateLimiter` (in-memory token bucket keyed by client IP, `forward-headers-strategy: framework` is already on):

| Endpoint | Limit |
|---|---|
| `GET /public/inspections/{token}` | 60 / min / IP |
| media stream | 300 / min / IP |
| `POST …/response` | 10 / min / IP |
| unknown-token (404) responses | 20 / 10 min / IP, then 15-min block with `429` |

Single-instance only (documented); move to a shared store (Redis / the edge proxy) if the API scales out. Request body of the response endpoint capped at 16 KB.

### 6.5 Response headers on `/public/**` and the page (`/i/**`)
`Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, `X-Robots-Tag: noindex, nofollow`,
`X-Frame-Options: DENY`, `Permissions-Policy: camera=(), microphone=(), geolocation=()`, `Strict-Transport-Security` (prod), and a strict CSP:
`default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; media-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'`
(no inline script or style, so the page ships as separate `.js` / `.css` files).

### 6.6 Output encoding
Staff-entered labels / notes are shown with `textContent` on the page (never `innerHTML`); the app renders them as plain `<Text>`. JSON is the only
transport; no server-side HTML templating of user data.

### 6.7 Privacy
Customer sees first name only; IP is stored **hashed** (HMAC with a server secret) purely as submission evidence; media is gone in 48 h; EXIF / GPS is stripped (§6.1);
nothing from the public surface is indexable.

### 6.8 Storage security
Private bucket, no anonymous policy; a **dedicated MinIO service account** limited to this one bucket (`s3:GetObject/PutObject/DeleteObject/ListBucket`),
never the root user, in the app; server-side encryption at rest (§8); TLS between API and MinIO outside the Docker network; lifecycle rule as a
backstop (§8).

## 7. Customer page hosting

Static files in `src/main/resources/static/i/` (`index.html`, `app.js`, `app.css`), no framework, no third-party scripts or fonts (keeps the CSP
trivial and the page small on weak mobile networks). A `/i/{token}` request forwards to `index.html`; `app.js` reads the token from `location.pathname`
and calls `/public/inspections/{token}`. Layout and behaviour: UI repo doc, section "Customer page".

## 8. MinIO and configuration

### 8.1 Local (`docker-compose.yml`, additive)
- `minio` service: image **pinned by version/digest**, ports published to `127.0.0.1` only (API 9000, console 9001), volume `cleancars-minio-data`,
  healthcheck (`mc ready` / `/minio/health/live`), root credentials from `scripts/.dev.env` with dev defaults.
- `minio-init` (run-once, like the existing `liquibase` service): uses `mc` to create the bucket, keep it **private**, create the app's **limited service
  account** + policy, and set the lifecycle rule (expire objects after 3 days = backstop for the sweeper).
- `scripts/dev-up.sh` also waits for `minio` healthy; `scripts/.dev.env.example` documents the new variables.

### 8.2 Prod-grade (new `docker-compose.prod.yml` + docs; the existing `backend-service-docker-compose.yml` is reconciled, not duplicated)
- **No published ports.** MinIO sits on an internal Docker network; only the API container can reach it. Console disabled or behind VPN / SSH tunnel.
- Credentials from Docker/host **secrets** (`MINIO_ROOT_USER_FILE` style), **not** in the compose file or git.
- TLS: MinIO behind the internal reverse proxy or its own certs (`/root/.minio/certs`); API uses `https://` to it when it leaves the host.
- **Encryption at rest** (SSE-S3 with `MINIO_KMS_SECRET_KEY`, or an external KMS) on the bucket.
- `restart: unless-stopped`, resource limits, log rotation, `read_only` root FS where possible, `no-new-privileges`, non-root user.
- **No backup of this bucket** by design: data is ephemeral (48 h). Mention in runbook so nobody adds it to backup jobs.
- Disk alarm: alert at 70 % — the sweeper plus lifecycle keep usage small; 20 images × 10 MB × few orders/day is modest, videos dominate.

### 8.3 Application config
```yaml
app:
  storage:                    # S3-compatible, works for MinIO and AWS S3
    endpoint: ${STORAGE_ENDPOINT}          # e.g. http://minio:9000 (internal)
    region: ${STORAGE_REGION:us-east-1}
    bucket: ${STORAGE_BUCKET:cleancars-inspections}
    access-key: ${STORAGE_ACCESS_KEY}
    secret-key: ${STORAGE_SECRET_KEY}
    path-style: true
  inspection:
    public-base-url: ${INSPECTION_PUBLIC_BASE_URL}      # e.g. https://app.mygarageone.com
    link-ttl-hours: 24
    media-ttl-hours: 48
    ip-hash-secret: ${INSPECTION_IP_HASH_SECRET}        # >= 32 chars
    upload:
      max-image-mb: 10            # per file
      max-video-mb: 50            # per file
      max-total-mb-per-inspection: 120
      max-images: 20
      max-videos: 3
      max-video-seconds: 30
      max-uploads-per-org-hour: 60
    jobs:
      media-sweeper: { enabled: true, cron: "0 */10 * * * *" }
```
- **Local profile**: dev defaults for MinIO + a dev-only hash secret (like the existing JWT dev secrets).
- **Test profile**: an in-memory `MediaStorage` fake, sweeper disabled (like `trial-expiry`), no MinIO needed in unit tests.
- **Stage / prod profiles**: all `STORAGE_*`, `INSPECTION_PUBLIC_BASE_URL`, `INSPECTION_IP_HASH_SECRET` **required, no defaults** → boot fails fast when missing
  (same pattern as `RAZORPAY_*`). Add them to the env-var header comments in both profile files.
- Dependency: AWS SDK v2 `software.amazon.awssdk:s3` (so prod can switch to S3 without code changes). No presigning is used.

### 8.4 The 48 h deletion — belt and braces
1. **Read time**: every media endpoint refuses past `media_expires_at` (§6.2).
2. **Sweeper job** every 10 min: for media whose inspection is past `media_expires_at`, delete the object, then set `deleted_at` / null `object_key`.
   Delete object **first**, DB second, so a crash leaves a retryable row rather than an orphan file. Failures are logged and retried next tick.
3. **Orphan sweep** (daily): list `org/*/inspection/*` objects older than 3 days with no row, delete them.
4. **MinIO lifecycle rule** (3 days) as the last backstop.

Checklist rows, notes, shares and decisions are **not** deleted by this.

## 9. Checklist template (fixed, version 1)

Keys are stable identifiers; labels live in the app (and page) so wording can change without a migration. Section → items:

**Car** — Brakes: `brakes`, `brake_pad_feel`, `brake_fluid`. Wheels & tires: `wheels`, `tire_condition`, `tire_pressure`.
Lights: `headlights`, `tail_brake_lights`, `indicators`. Engine & fluids: `engine_oil`, `coolant`, `battery`.
Comfort & safety: `wipers`, `suspension`, `steering`, `ac_cooling`, `horn`.

**Bike** — Brakes: `brakes`, `brake_pad_feel`. Wheels & tires: `wheels`, `tire_condition`, `tire_pressure`. Drive: `chain_sprocket`, `clutch`.
Lights: `headlight`, `tail_brake_light`, `indicators`. Engine & fluids: `engine_oil`, `coolant`, `battery`. Other: `suspension`, `horn`.

Status meaning (shown on page and app): GREEN = good, YELLOW = needs attention soon, RED = needs attention now, NOT_CHECKED = skipped.
Rendered with an icon and a text label as well as the colour (colour-blind safe).

## 10. Error codes (additions to `ConflictException` / handler)

`inspection_exists`, `inspection_locked`, `inspection_empty`, `order_cancelled`, `invalid_media` (+ reason: `unsupported_type`, `too_large`, `too_long`, `corrupt`, `limit_reached`, `inspection_size_limit`), `already_responded`, `link_expired`, `link_revoked`, `rate_limited` (429).

## 11. Tests

- Unit: `MediaValidator` — per-file limits at exactly the limit and +1 byte; wrong magic bytes with `.jpg` name, polyglot (valid JPEG + trailing ZIP) comes out clean after re-encode, EXIF GPS stripped,
  oversize, decompression bomb dimensions, video too long, truncated MP4.
- Service: total-size quota (the upload that crosses 120 MB is refused, a delete frees room, two parallel uploads cannot both pass); share lifecycle (new link revokes old; expiry at 24 h; revoke), single-submit race (two threads → one `409`), answer set must equal snapshot,
  edits to the order after sending do not change what the customer sees, `RESPONDED` locks checklist edits.
- Security web tests (`MockMvc`): `/public/**` needs no JWT, `/api/inspections/**` does; org A cannot read / stream org B's inspection or media;
  token for inspection X cannot stream media of inspection Y; expired media → 410 before the sweeper runs; response DTO contains no phone / ids outside the allow-list;
  security headers present; rate limiter returns 429.
- Sweeper: with the fake storage, expired media objects are gone and rows tombstoned; non-expired untouched; storage failure leaves the row for retry.
- Integration (opt-in, local): upload → view → respond against the real MinIO container.

## 12. Deferred / open

1. **Push to staff when the customer responds** (v1: the order shows the live status when opened).
2. **Send from the server** (WhatsApp/SMS via Twilio, already a dependency) as an alternative to the share sheet.
3. **ffmpeg stage** for video: transcode to H.264/AAC ≤ 720p, strip metadata, true duration check. Adds an image dependency — decide after v1 load is known.
4. ClamAV scan hook implementation.
5. **Plan gating / kill switch**: a `FeatureProperty` flag (like `Client.Maintenance.Mode.Enable`) to turn DVI off globally — recommended; per-plan gating like `amcEnabled` only if you want it sold as an add-on.
6. **Checklist wording**: confirm the §9 lists (or send yours).
7. Link valid 24 h but media lives 48 h from inspection creation: a link sent late (e.g. hour 40) outlives the media. The page then shows
   "photos are no longer available" and the checklist + answers still work. Confirm that is acceptable.
