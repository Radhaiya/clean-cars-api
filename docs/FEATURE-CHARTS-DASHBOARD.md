# Charts, KPI tiles, org totals & dashboard

## What it does

Four related read-only reporting endpoints, all real data (no mocks):

**`GET /api/charts`** (`metric` = `TOTAL_SERVICE` | `TOTAL_REVENUE`/`AMC_REVENUE`/`TOTAL_PROFIT` (revenue + AMC − expenses per bucket, same as the Profit tile),
`granularity` = `DAY`|`WEEK`|`MONTH`|`YEAR`, `from`/`to` required, inclusive) —
returns calendar-aligned, zero-filled buckets. `TOTAL_SERVICE` counts
non-cancelled service orders by `createdAt` (AMC redemption orders included — they are ₹0, so they add to counts but not revenue; AMC *sales* appear only through the `AMC_REVENUE` / `TOTAL_PROFIT` metrics, never in `TOTAL_REVENUE` — see `docs/FEATURE-AMC.md`); `TOTAL_REVENUE` sums
`TaxBreakdown.net()` (net of any line discount) across the **received** share of every order (split
payments: a half-paid order counts half its net — see `docs/FEATURE-SPLIT-PAYMENTS.md`). Gated by the
plan's `stats_range_years` via `PlanLimitService.assertStatsRangeAllowed` —
no live subscription or `stats_range_years = 0` → `409 statistics_not_available`;
`from` reaching further back than the plan allows → `409 stats_range_exceeded`.

**`GET /api/charts/kpi-tiles`** (`from`/`to` only, no metric/granularity) —
a flat P&L total: `{ totalRevenue, totalExpenses, totalProfit }`, both terms
net of tax. Shares the same `stats_range_years` gating as the buckets
endpoint. Also range-scoped (no lookback outside `[from, to]`):
`averageServiceValue` (`totalRevenue` + each non-cancelled AMC redemption's single-use net price (lines at full price, before the 100% discount) ÷ (paid-order count + AMC redemption count) in range — redemptions are for this average only, never added to `totalRevenue`; `0.00`
when there are none — a ticket-size figure comparable period over period),
`newCustomers` (customers whose record was first created in range, a proxy
for first-ever visits), `amcRevenue` (Σ net-of-tax `amc_subscriptions.sale_net` with
`payment_date` in range and not after today (org timezone) — future-dated sales are not counted yet; separate from `totalRevenue`, but counted in `totalProfit = totalRevenue + amcRevenue − totalExpenses`), and `revenueByPaymentType` (the same paid,
net-of-tax revenue split by `PaymentType`'s name — always has an entry for
every type, `0.00` when unused, plus an `UNCATEGORIZED` entry for paid
orders with no payment type recorded, still counted in `totalRevenue`),
`expensesByCategory` (`totalExpenses` split by the expense's denormalized
`categoryName`, ranked highest first — only categories actually used in
range appear, unlike the fixed `PaymentType` set), and `revenueByEmployee`
(each employee's job count — non-`CANCELLED` orders in range — and paid
net-of-tax revenue in range, ranked by revenue highest first; job
count/revenue are independent populations so one can be zero while the
other isn't; orders with no assigned employee are rolled into one
`{ employeeId: null, employeeName: "Unassigned" }` row rather than dropped).

**`GET /api/charts/totals`** — no params, **not** gated by `stats_range_years`
(these are basic all-time counts, not a "statistics" feature). Returns
`{ totalCars, totalServices, totalEmployees, totalCustomers }` (and
`totalBikes` — see `docs/UI-BIKES.md`). `totalServices` is the size of the
service catalog, not a count of job cards. Originally lived on
`GET /api/dashboard`, moved here on request.

**`GET /api/dashboard`** — the org's home-page snapshot, also not gated by
`stats_range_years` (always-available operational overview, distinct from the
premium "statistics" pages). Returns (`DashboardResponse`): `servicesInProgress` (= `carsInProgress` + `bikesInProgress`),
`servicesUnpaid`, `unpaidAmount`, `todayRevenue`/`yesterdayRevenue` (`{paid, unpaid}`, keyed by the org's local
day via the timezone conversion described in `docs/FEATURE-TIMEZONES.md`) + `todayRevenueChangePct`, the rolling-30-day customer
trend (`totalCustomers`, `newCustomersLast30Days` / `…Previous30Days`, `customersChangePct`), `monthlyFinancials` (12 entries for the
current calendar year `{month, earnings, expenses}` — earnings paid-only net-of-tax, expenses gross; both `null` for future months),
`todaysServices` and `recentCustomers`. **`GET /api/dashboard/earnings?year=`** returns the same 12-entry `monthlyFinancials` shape for
another year (the chart's year picker). The UI-side field reference is `clean-cars-ui/docs/dashboard-api-spec.md`.

## Files that implement this

| File | Role |
|---|---|
| `controller/ChartsController.java` | `GET /api/charts`, `/kpi-tiles`, `/services`, `/amcs`, `/amc-usage`, `/amc-expiring`, `/top-customers`, `/totals`. |
| `service/ChartService.java` | Bucketing, `TOTAL_SERVICE`/`TOTAL_REVENUE` aggregation, `enforceStatsRange` (shared gating logic), `getKpiTiles`, `getTotals`. |
| `dto/ChartBucket.java` | `{ periodStart, periodEnd, value }` bucket shape. |
| `dto/ChartMetric.java`, `dto/ChartGranularity.java` | Query-param enums. |
| `dto/OrderRevenue.java`, `service/OrderRevenueReader.java` | Per-order net/gross/amountPaid/receipts — the single source of paid/unpaid money (see `docs/FEATURE-SPLIT-PAYMENTS.md`). |
| `dto/KpiTilesResponse.java` | `{ totalRevenue, totalExpenses, totalProfit, totalServices, averageServiceValue, newCustomers, revenueByPaymentType, expensesByCategory, revenueByEmployee }`. |
| `dto/EmployeeJobCountRow.java` | Internal projection — per-employee job count, feeding `revenueByEmployee`. |
| `dto/EmployeeRevenueResponse.java` | One `revenueByEmployee` entry: `{ employeeId, employeeName, totalServices, totalRevenue }`. |
| `controller/DashboardController.java` | `GET /api/dashboard`, `GET /api/dashboard/earnings?year=`. |
| `service/DashboardService.java` | Builds the dashboard snapshot; depends on `ServiceOrderRepository`/`ExpenseRepository`, and the org-timezone resolver for "today"/"yesterday" boundaries. |
| `dto/DashboardResponse.java`, `dto/DashboardServiceRow.java`, `dto/DashboardRecentCustomer.java` | Dashboard response shapes. |
| `dto/TaxBreakdown.java` | Shared net/tax/gross math both charts and the dashboard build totals on. |
| `service/internal/PlanLimitService` | `assertStatsRangeAllowed` — the gating check both `/api/charts` and `/api/charts/kpi-tiles` call before computing anything. |
| Repositories: `ServiceOrderRepository`, `ExpenseRepository`, `CustomerRepository`, `EmployeeRepository` | Source queries (`findCreatedAtForServiceCount`, `findJobCountsByEmployee`, `findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan`, `findByOrgIdAndPaidFalseAndStatusNot`). |

## Breakdown bar charts — `GET /api/charts/services`, `GET /api/charts/amcs`

Both take `from`/`to` (inclusive, same `stats_range_years` gating) and return unsorted `ChartBreakdownRow[]`
`{name, count, revenue}` (net of tax); the UI ranks top 10 by its Count/Revenue toggle.
`/services` (`OrderRevenueReader.serviceBreakdown`): non-cancelled, non-AMC-redemption orders created in range,
grouped by service **name** (case-insensitive — order lines don't store the catalog id); count = line quantity,
revenue = the line's net share of money actually received. `/amcs` (`AmcSubscriptionRepository.sumSalesByPlan`):
per AMC plan (variants combined), count sold + Σ `sale_net`, `payment_date` in range and not after today.

## AMC snapshots — `GET /api/charts/amc-usage`, `GET /api/charts/amc-expiring`

No date range (state as of today, org timezone); both need `amc_enabled` (`409 amc_not_in_plan`). `AmcInsightsService`
derives everything at runtime from `AmcSlots`/`AmcSubscriptionAssembler`. `/amc-usage`: Σ `used` / `remaining` / `lapsed`
over every AMC ever sold, plus the same per plan name. `/amc-expiring`: ACTIVE AMCs with end date today … today+30 days,
soonest first, with vehicle number/kind, owner name + phone, plan, `daysLeft`, `remaining` visits.

## Top customers — `GET /api/charts/top-customers`

Lifetime (no date range, not plan-gated), `TopCustomersService`: per customer, net-of-tax `serviceRevenue` (money received
on every order they ever had, `OrderRevenueReader.lifetimeByCustomer`) + `amcRevenue` (`sale_net` of AMCs sold — payment
date not after today — to the cars/bikes they currently own; an AMC follows its vehicle) = `totalRevenue`. Top 10, deleted
customers and zero-revenue customers excluded; also returns `jobs` (non-cancelled orders) and `amcsBought`.
