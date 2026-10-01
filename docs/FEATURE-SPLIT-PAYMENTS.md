# Split payments (one-time vs split) on service orders

## What it does

Each service order (job card) has a **payment plan**: `ONE_TIME` (default) or `SPLIT`.
Both plans write to the **same ledger** — the `payments` table, one row per receipt of
money — so there is one code path for totals, `paid`, dashboard and statistics.

| | `ONE_TIME` | `SPLIT` |
|---|---|---|
| payments allowed | exactly one | any number |
| amount | always the order's gross total (body `amount` ignored) | `amount` required, `0.01 ≤ amount ≤ remaining` |
| "mark unpaid" | delete the payment | delete any split |

Design choice: a one-time order is *a split list of length 1 whose amount is forced to the
total* — not a separate mechanism. The only plan-specific code is `ServiceOrderPaymentLedger.resolveAmount`.

**Line discount & free orders:** a line's per-unit `discountAmount` comes off its price before tax (`TaxBreakdown.ofLine`), so every
total below (gross, `amountRemaining`, revenue) is net of discounts. An order with ≥ 1 line and a gross total of 0 (100% discount, or an
AMC redemption) is `paid = true` automatically with no payment row; payments on it are refused (`order_total_zero`). An AMC redemption
order additionally refuses any payment / plan change (`amc_order_locked`). See `docs/FEATURE-AMC.md`.

**Derived values** (never written directly):
`amountPaid` = Σ payments · `amountRemaining` = `max(0, grossTotal − amountPaid)` ·
`paid` = `amountPaid > 0 && amountPaid ≥ grossTotal`. `service_orders.amount_paid` and `.paid`
are denormalized (so the list's `?paid=` filter and dashboard counts stay simple queries) and
are recomputed by `ServiceOrderPaymentLedger.refresh` after every payment write **and** after
the order's lines are replaced (a changed total re-derives `paid`).

## API (`/api/service-orders/{id}/…`)

- `PATCH /payment-plan` `{paymentPlan}` — switch plans. `SPLIT → ONE_TIME` only while the order has no payments (`409 payment_plan_has_payments`).
- `POST /payments` `{amount?, paymentType, paymentDate?}` → 201 + the full order. `paymentDate` defaults to today (org timezone).
- `PUT /payments/{paymentId}` — same body. Amount is re-validated against what the *other* payments leave; a one-time payment re-snaps to the current total.
- `DELETE /payments/{paymentId}` → the order with recomputed totals.
- `POST/PUT /api/service-orders` also accept `payments: [{id?, amount?, paymentType, paymentDate?}]` (the order form): null leaves payments untouched, a list (even empty) **replaces** them all — lines with a known `id` are kept/updated, others created, unlisted ones deleted — under the plan's rules (one-time: max 1, amount forced to total; split: sum ≤ total). Applied after the lines are saved (`ServiceOrderPaymentLedger.replacePayments`).
- `POST/PUT /api/service-orders` accept `paymentPlan` (null → `ONE_TIME` on create, unchanged on update). The old `paid` / `paymentType` / `paymentDate` request fields and `PATCH /{id}/paid` are **gone**.
- Responses (`ServiceOrderResponse`): `paymentPlan, paid, amountPaid, amountRemaining, payments[]` where each payment has `remainingAfter` (running balance after it, by date then record time). List rows (`ServiceOrderSummaryResponse`) carry `paymentPlan, paid, amountPaid, amountRemaining`. Vehicle-history rows (`CarServiceSummary`) gain `amountPaid`.
- Error codes (409): `payment_exceeds_remaining`, `one_time_already_paid`, `order_total_zero`, `payment_plan_has_payments`. Missing amount on a split → 400.
- Payment writes (and order update) lock the order row (`lockByIdAndOrgId`) so two concurrent splits can't both pass the overpayment check.
- Not blocked: editing an order's lines so the total drops below what was received (remaining shows 0, `paid` true). Raising it reopens the order.

## Stats & dashboard (money actually received)

All revenue reporting goes through `OrderRevenueReader` → `OrderRevenue` (order net/gross from lines + `amountPaid` + its receipts):

- `paidNet = net × min(1, amountPaid / gross)` — a half-paid order counts half its net as paid, half as unpaid, at the order's own tax ratio.
- **`GET /api/charts`** `TOTAL_REVENUE`, **kpi-tiles** `totalRevenue` / `averageServiceValue` (÷ orders with `amountPaid > 0`) / `revenueByEmployee`: Σ `paidNet`.
- **kpi-tiles** `revenueByPaymentType`: each order's `paidNet` apportioned across its payments' methods by amount (legacy migrated orders with no method → `UNCATEGORIZED`).
- **`GET /api/dashboard`**: `todayRevenue`/`yesterdayRevenue` `{paid, unpaid}` = Σ `paidNet` / Σ `unpaidNet` (non-cancelled); `unpaidAmount` = Σ (`gross − amountPaid`) over non-cancelled, not-fully-paid orders (all-time); `servicesUnpaid` counts orders with `paid = false` (part-paid included); `monthlyEarnings` = Σ `paidNet`.
- Bucketing is unchanged: by the **order's created date**, not the payment date.

## Migration `011-split-payments.sql`

Reuses the (previously unused) `payments` table: adds `org_id`, `payment_date`, `mode` → `card|cash|upi|NULL`, microsecond `created_at`; drops `paid_at`/`reference_number`. `service_orders` gains `payment_plan`, `amount_paid` and loses `payment_type`/`payment_date`. Every legacy `paid = 1` order becomes one full-gross payment (keeping its method/date; line gross computed in SQL exactly as `TaxBreakdown` does). Legacy "paid" orders with a zero total have nothing received and become unpaid. Rollback restores the columns (latest payment's method/date).

## Files

| File | Role |
|---|---|
| `entity/PaymentPlan(+Converter)`, `entity/Payment`, `repository/PaymentRepository` | plan enum, ledger row, finders |
| `entity/ServiceOrder` | `paymentPlan`, `amountPaid`, `paid` (derived) |
| `service/ServiceOrderPaymentLedger` | the rules: `resolveAmount`, `changePlan`, `refresh`, `grossTotal` |
| `service/ServiceOrderPayment{Create,Update,Delete}Service` | payments sub-resource (reads come with the order) |
| `service/ServiceOrderUpdateService.setPaymentPlan` | plan quick-edit |
| `dto/PaymentRequest`, `PaymentPlanRequest`, `PaymentResponse` | payloads + running balance |
| `dto/OrderRevenue`, `service/OrderRevenueReader` | paid/unpaid money for charts + dashboard |
| `test/SplitPaymentTest` | rules, derived paid, dashboard numbers |
