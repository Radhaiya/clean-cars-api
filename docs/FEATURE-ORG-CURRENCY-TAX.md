# Organization currency & tax name

## What it does

Two small, independent, display-only settings on `Organization`:

**Currency** — `organizations.currency_code` (ISO 4217, e.g. `INR`) +
`currency_symbol` (e.g. `₹`), migration `002-organization-currency.sql`.
Compulsory (code only) on `POST /api/subscription/trial`, optional on
`PUT /api/organization` (null = unchanged). The server resolves code → symbol
via JDK locale data and stores both; an unknown code is a 400. Display-only —
no amounts are converted anywhere; Razorpay billing stays INR regardless of
the org's chosen currency.

**Tax name** — `organizations.tax_name` (`VARCHAR(64)`, migration
`007-org-tax-name.sql`) — a **display label only** (`VAT`, `GST (India)`,
`Sales Tax`, ...), not a rate. `GET /api/reference` lists a curated set of
labels (deliberately no "No Tax" entry — an exempt org just clears the
field). Optional on trial creation; on `PUT /api/organization` it follows the
full-replace convention (omitted/null = cleared). The actual tax **rate**
(`taxPercentage`) always stays per line item (`TaxBreakdown`), never at the
org level — this field is purely cosmetic labeling for invoices/UI.

Both are surfaced together on `GET /api/organization` and `GET /api/me`
(`orgTaxName` next to `orgTimezone`).

## Files that implement this

| File | Role |
|---|---|
| `entity/Organization.java` | `currencyCode`, `currencySymbol`, `taxName` columns. |
| `service/ReferenceDataService.java` | `requireCurrency`/`symbolOf` (code → symbol resolution, unknown code → 400) and the curated `taxes` pick-list. |
| `controller/ReferenceDataController.java` | `GET /api/reference` returns `currencies` and `taxes` pick-lists. |
| `service/SubscriptionService.java` | Validates/persists `currency` (compulsory) and `taxName` (optional) when a trial/org is created. |
| `dto/OrganizationUpdateRequest.java` | Carries `currencyCode`/`taxName` on `PUT /api/organization` (full-replace semantics). |
| `dto/StartTrialRequest.java` | Carries `currency`/`taxName` on `POST /api/subscription/trial`. |
| `dto/internal/InternalOrgSummaryResponse.java`, `dto/internal/InternalOrgDetailResponse.java` | Expose currency/tax fields to the internal console. |
| `dto/UserProfile.java` | `orgTaxName` returned by `GET /api/me`. |
| `dto/TaxBreakdown.java` | Per-line-item tax math (net/tax/gross) — unrelated to `tax_name`, but the thing `tax_name` is a display label *for*. |
| `db/changelog/migrations/002-organization-currency.sql` | Adds `currency_code`/`currency_symbol`, backfills existing rows `INR`/`₹`. |
| `db/changelog/migrations/007-org-tax-name.sql` | Adds `tax_name`. |
