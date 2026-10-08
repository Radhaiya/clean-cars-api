# AMC (Annual Maintenance Contract) + line discounts

Status: **implemented** (migrations 014–018). Reporting: only the `amcRevenue` KPI tile is built; charts / dashboard are deliberately **not built yet**
— see "Open / deferred". UI counterpart: `clean-cars-ui/docs/features/amc.md`.

## Concept

A garage defines **AMC plans** (a fixed bundle of service names). Each plan has **variants** (tenure + frequency + per-service
prices). A variant is **sold to a car or bike** (never a customer) for an upfront, fully-paid price. While the AMC is active the
vehicle may redeem **one whole bundle per period ("slot")**. Redemption creates a service order whose lines are the bundle at a
**hard-set 100% discount** (₹0, never a bill). Nothing is stored per slot — used / lapsed / remaining are computed at runtime from
dates and redemption orders.

## Rules

- **Bundle only.** A redemption is the entire bundle — no extra, no fewer services. Anything else is a normal (paid) service order.
- **Plan service names are fixed** (`amc_plan_items`, never edited). Different names ⇒ a different plan. Variants differ in tenure,
  frequency and prices only (e.g. Gold: 1y/monthly and Gold: 2y/monthly with different prices).
- **Frequency is generic:** `interval_months` (1 = monthly, 12 = yearly, 3 = quarterly…). Tenure is stored in months. **Tenure must be
  a multiple of the interval**, else 400 (and a DB `CHECK`). `total_slots = tenure_months / interval_months`.
- **Pricing & tax are per service row — there is no flat AMC price.** A row has `quantity` (default 1), `price` (per unit), tax % and a
  tax-included flag (same semantics as service-catalog lines). Row = `price × quantity` (tax per row); bundle = Σ rows; **sale total =
  bundle × `total_slots`**, paid upfront. Rows are editable in the variant and again at sale; the total always follows from the rows.
- **Snapshot at sale.** Service names, per-row quantity / price / tax, tenure, interval and plan name are copied onto the sold AMC
  (`amc_subscription_items`); later edits to the plan / variant never change it, and everything on a row is editable at sale.
- **Sale payment:** full, one payment (method + date, defaults to today), recorded on the subscription (`payment_mode`, `payment_date`,
  amount = `sale_gross`) — a **separate revenue stream from service revenue**. Optional **seller employee**. No cancellation or refund.
- **Start date** defaults to today, **cannot be in the past**, at most ~1 year ahead.
- **Slots are anniversary-based** from the start date: slot *i* (0-based) covers `[start + i·interval, start + (i+1)·interval)` months,
  each boundary computed from the original start (end-of-month clamping never drifts). The AMC ends at `start + tenure`.
  Logic: `service/AmcSlots` (pure, unit-tested).
- **Strict slot usage — no early redemption.** Only the *current* slot can be redeemed; a past slot nobody redeemed is **lapsed**
  (no log, no price, no carry-forward). The slot a visit uses is "today" in the org timezone (no backdating).
- **Counts (runtime):** `total`, `used` (slots with a non-cancelled redemption order), `lapsed` (past slots never redeemed),
  `remaining` (current + future slots not used), `availableNow` (current slot unused → 1 else 0), `currentSlot`,
  `status` = `UPCOMING` (not started) / `ACTIVE` / `EXPIRED`.
- **A cancelled or deleted redemption order frees its slot.** A cancelled AMC order can **never be reopened** (409 `amc_order_reopen`);
  the user creates a new order. So a slot can never hold two live redemptions; creating one while another live order holds the slot
  → 409 `amc_slot_used`, and the subscription row is `PESSIMISTIC_WRITE`-locked so cancel + create races are safe.
- **One AMC → one service order.** Several AMCs on a vehicle (even the same plan twice) are allowed; redeem each separately.
- **Explicit selection** in the service-order form (never auto-applied).
- **AMC belongs to the vehicle.** Ownership transfer does not affect it; no customer is stored.
- **Archive vs delete:** archived plans / variants cannot be sold (already-sold AMCs keep working). Only a plan / variant that was
  **never sold** can be deleted (409 `amc_plan_sold` / `amc_variant_sold` otherwise).
- **Roles:** owner / manager (and legacy admin) create, edit, archive and delete plans and variants. Anyone in the org can read plans,
  sell an AMC and redeem it.
- **Plan gating:** `subscription_plans.amc_enabled` (`NOT NULL DEFAULT TRUE` — every plan has it, flipped per plan by the operator).
  `PlanLimitService.assertAmcEnabled` → 409 `amc_not_in_plan` on every AMC endpoint. Exposed to the apps as
  `GET /api/me` → `plan.amcEnabled` (and `GET /api/plans` → `features.amcEnabled`).

## Line discount (applies to ALL service orders)

- **Per line, stored as an AMOUNT**, never a percent: `service_order_items.discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0`,
  `0 ≤ amount ≤ unit price`. **Per unit** — it comes off each unit's price, so the derived % is independent of `quantity`
  (₹50 off a ₹500 unit = 10% at any quantity). The percent is derived on read (`ServiceOrderItemResponse.discountPercent`).
- Applied to the price **before** tax; for a tax-included line it comes off the displayed (gross) price (₹11.80 off ₹118 @18% incl. =
  ₹106.20 gross). Full price off = free including tax. Implemented once in `TaxBreakdown.ofLine(item)` / `afterDiscount`, so order
  totals, vehicle history, payments ledger, revenue reader, dashboard and the list's total filter/sort (`ServiceOrderSpecs`) all agree.
- Request: `ServiceOrderItemRequest.discountAmount` (null = 0; > price → 400). Response: `discountAmount`, `discountPercent`.
- **Zero-total orders are paid automatically:** an order with ≥ 1 line and gross 0 is `paid = true` with no payment row
  (`ServiceOrderPaymentLedger.refresh`, run on create and update). Migration 014 back-filled this for existing all-zero-priced orders.
  Recording a payment on a zero-total order is still refused (`order_total_zero`).
- **AMC redemption lines:** one per AMC row, `basePrice` / tax / quantity copied from the sold AMC, `discount_amount` = the full unit
  price. Built by the server only; the client cannot send lines, prices, discount or payments for an AMC order.

## Data model (migrations)

| Migration | Adds |
|---|---|
| `014-line-discount.sql` | `service_order_items.discount_amount` (+ zero-total `paid` back-fill) |
| `015-amc-plans.sql` | `amc_plans`, `amc_plan_items`, `amc_plan_variants`, `amc_variant_rows`; `subscription_plans.amc_enabled` |
| `016-amc-subscriptions.sql` | `amc_subscriptions` (car_id XOR bike_id `CHECK`, snapshot + payment columns, seller), `amc_subscription_items` |
| `017-amc-redemption.sql` | `service_orders.amc_subscription_id`, `amc_slot_index` (+ FK / index) |
| `018-amc-row-quantity.sql` | `quantity` on `amc_variant_rows` and `amc_subscription_items` |

| Table | Key columns |
|---|---|
| `amc_plans` | id, org_id, name (unique per org), archived |
| `amc_plan_items` | plan_id, service_name (unique per plan), position |
| `amc_plan_variants` | id, plan_id, org_id, tenure_months, interval_months (unique per plan), archived |
| `amc_variant_rows` | variant_id, plan_item_id, quantity, price, tax_percentage, tax_included |
| `amc_subscriptions` | id, org_id, car_id / bike_id, plan_id, variant_id, plan_name, tenure_months, interval_months, start_date, sale_net/tax/gross, payment_mode, payment_date, received_by, sold_by_employee_id |
| `amc_subscription_items` | subscription_id, service_name, position, quantity, price, tax_percentage, tax_included |
| `service_orders` (+) | amc_subscription_id, amc_slot_index |

All business rows carry `org_id` from `AuthContext.requireOrgId()`; finders are org-scoped (foreign ids → 404). `src/main/resources/cleancars_schema (1).dbml`
mirrors the schema.

## API

**Plans & variants — `AmcPlanController`, `/api/amc-plans`** (reads: any org member; writes: owner/manager; all need `amc_enabled`)

| Method / path | Notes |
|---|---|
| `GET /api/amc-plans?search&includeArchived&page&size` | paged; each plan carries its service names and variants |
| `GET /api/amc-plans/{id}` | one plan |
| `POST /api/amc-plans` `{name, serviceNames[]}` | services fixed afterwards; 409 `amc_plan_name_exists` |
| `PUT /api/amc-plans/{id}` `{name}` | rename only |
| `PATCH /api/amc-plans/{id}/archived` `{archived}` | |
| `DELETE /api/amc-plans/{id}` | never-sold only (409 `amc_plan_sold`) |
| `POST /api/amc-plans/{id}/variants` `{tenureMonths, intervalMonths, rows[{serviceName, quantity?, price, taxPercentage?, taxIncluded}]}` | one row per plan service; 400 on bad shape; 409 `amc_variant_exists` / `amc_plan_archived` |
| `PUT /api/amc-plans/{id}/variants/{vid}` | same body |
| `PATCH /api/amc-plans/{id}/variants/{vid}/archived` | |
| `DELETE /api/amc-plans/{id}/variants/{vid}` | never-sold only (409 `amc_variant_sold`); returns the plan |
| `GET /api/amc-plans/{id}/sales` | totals + per-variant `{totalSold, active, upcoming, expired, soldGross}` and `currentSales` (active first, then upcoming: vehicle kind / number, current owner, the AMC's runtime counts) |

Every plan / variant write returns the full updated plan. Response rows: `quantity`, per-unit `price`, tax, and line-total `net/tax/gross`;
variants add `bundle*` (Σ rows) and `total*` (× `totalSlots`).

**Sales & redemption — `AmcSubscriptionController`, `/api/amc-subscriptions`** (any org member)

| Method / path | Notes |
|---|---|
| `POST /api/amc-subscriptions` `{carId\|bikeId, variantId, startDate?, rows?[…], sellerEmployeeId?, paymentType, paymentDate?}` | sells; `rows` override the variant's rows for this sale; 409 `amc_variant_archived` |
| `GET /api/amc-subscriptions?carId=…\|bikeId=…` · `GET /{id}` | a vehicle's AMCs (all statuses, newest first) with runtime counts |
| `POST /api/amc-subscriptions/{id}/redeem` `{employeeId?, odometerReading?, vendorId?, notes?}` | 201 + the service order. 409 `amc_not_started` / `amc_expired` / `amc_slot_used`. Vehicle is the AMC's (live owner required). |

`ServiceOrderResponse.amc` = `{subscriptionId, planName, useNumber (1-based), totalSlots}` on redemptions; the order list's
`ServiceOrderSummaryResponse.isAmc` flags them.

**AMC order locks** (`ServiceOrderUpdateService`, `ServiceOrderPaymentLedger`): `PUT` with lines or payments, adding a payment, or
changing the plan → 409 `amc_order_locked`; only assignee / odometer / vendor / notes / status change. Leaving CANCELLED → 409
`amc_order_reopen`. Deleting the order frees its slot.

**Error codes (409):** `amc_not_in_plan`, `amc_plan_name_exists`, `amc_plan_archived`, `amc_variant_exists`, `amc_variant_archived`,
`amc_plan_sold`, `amc_variant_sold`, `amc_slot_used`, `amc_expired`, `amc_not_started`, `amc_order_locked`, `amc_order_reopen`.

## Files

| File | Role |
|---|---|
| `entity/AmcPlan`, `AmcPlanItem`, `AmcPlanVariant`, `AmcVariantRow` | plan template (+ repositories `Amc*Repository`) |
| `entity/AmcSubscription`, `AmcSubscriptionItem` | sold AMC + snapshot |
| `service/AmcPlan{Create,Read,Update,Delete}Service`, `AmcVariant{Create,Update}Service`, `AmcVariantRowFactory`, `AmcPlanAssembler`, `AmcPlanGuard` | plan / variant CRUD, validation, role + plan-gate checks |
| `service/AmcPlanSalesService` | the plan detail's sales picture |
| `service/AmcSubscriptionCreateService`, `AmcSubscriptionReadService`, `AmcSubscriptionAssembler` | sale + reads with runtime counts |
| `service/AmcSlots` | pure slot / lapse math |
| `service/AmcRedemptionService` | redeem (row lock, strict slot, builds the 100%-discount lines) |
| `dto/Amc*` | request / response records |
| `controller/AmcPlanController`, `AmcSubscriptionController` | endpoints |
| `dto/TaxBreakdown` (`ofLine`, `afterDiscount`) | discount-aware line math |
| `service/internal/PlanLimitService.assertAmcEnabled` | plan gate |
| tests | `LineDiscountTest`, `AmcSlotsTest`, `AmcPlanTest`, `AmcSaleTest`, `AmcRedemptionTest` |

## Open / deferred

- **AMC revenue reporting**: only the `amcRevenue` KPI tile exists (net `sale_net` by payment date, counted in `totalProfit`, excluded from `totalRevenue` / `averageServiceValue`). Charts / dashboard: not built. Open questions to settle first — recognise at the sale payment date
  vs spread over the tenure, net vs gross, whether it counts toward profit, AMC revenue by payment method / by seller, and whether
  redemption orders (₹0) count in service counts. `OrderRevenueReader` / charts deliberately exclude AMC sales today.
- A list of all sold AMCs with "expiring soon" (renewals), and a printable sale receipt / invoice.
- AMC endpoints are not yet in the Postman collection.
