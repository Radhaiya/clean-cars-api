# Charts, KPI tiles, org totals & dashboard

## What it does

Four related read-only reporting endpoints, all real data (no mocks):

**`GET /api/charts`** (`metric` = `TOTAL_SERVICE` | `TOTAL_REVENUE`,
`granularity` = `DAY`|`WEEK`|`MONTH`|`YEAR`, `from`/`to` required, inclusive) —
returns calendar-aligned, zero-filled buckets. `TOTAL_SERVICE` counts
non-cancelled service orders by `createdAt`; `TOTAL_REVENUE` sums
`TaxBreakdown.net()` across every line of every **paid** order. Gated by the
plan's `stats_range_years` via `PlanLimitService.assertStatsRangeAllowed` —
no live subscription or `stats_range_years = 0` → `409 statistics_not_available`;
`from` reaching further back than the plan allows → `409 stats_range_exceeded`.

**`GET /api/charts/kpi-tiles`** (`from`/`to` only, no metric/granularity) —
a flat P&L total: `{ totalRevenue, totalExpenses, totalProfit }`, both terms
net of tax. Shares the same `stats_range_years` gating as the buckets
endpoint. Also range-scoped (no lookback outside `[from, to]`):
`averageServiceValue` (`totalRevenue` ÷ paid-order count in range, `0.00`
when there are none — a ticket-size figure comparable period over period),
`newCustomers` (customers whose record was first created in range, a proxy
for first-ever visits), and `revenueByPaymentType` (the same paid,
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
premium "statistics" pages). Returns `servicesInProgress`, `servicesUnpaid`,
`todayRevenue`/`yesterdayRevenue` (`{paid, unpaid}`, keyed by the org's local
day via the timezone conversion described in `docs/FEATURE-TIMEZONES.md`), and
`monthlyEarnings` (12 entries for the current calendar year, paid-only
net-of-tax revenue, `null` for future months).

## Files that implement this

| File | Role |
|---|---|
| `controller/ChartsController.java` | `GET /api/charts`, `/api/charts/kpi-tiles`, `/api/charts/totals`. |
| `service/ChartService.java` | Bucketing, `TOTAL_SERVICE`/`TOTAL_REVENUE` aggregation, `enforceStatsRange` (shared gating logic), `getKpiTiles`, `getTotals`. |
| `dto/ChartBucket.java` | `{ periodStart, periodEnd, value }` bucket shape. |
| `dto/ChartMetric.java`, `dto/ChartGranularity.java` | Query-param enums. |
| `dto/ChartRevenueLine.java` | Projection used when summing paid/unpaid revenue lines. |
| `dto/KpiTilesResponse.java` | `{ totalRevenue, totalExpenses, totalProfit, totalServices, averageServiceValue, newCustomers, revenueByPaymentType, expensesByCategory, revenueByEmployee }`. |
| `dto/PaymentTypeRevenueRow.java` | Internal projection — a paid order-item line carrying its order's `paymentType`, feeding the kpi-tiles breakdown. |
| `dto/EmployeeJobCountRow.java`, `dto/EmployeeRevenueRow.java` | Internal projections — per-employee job count and paid revenue lines, feeding `revenueByEmployee`. |
| `dto/EmployeeRevenueResponse.java` | One `revenueByEmployee` entry: `{ employeeId, employeeName, totalServices, totalRevenue }`. |
| `controller/DashboardController.java` | `GET /api/dashboard`. |
| `service/DashboardService.java` | Builds the dashboard snapshot; depends on `ServiceOrderRepository`/`ExpenseRepository`, and the org-timezone resolver for "today"/"yesterday" boundaries. |
| `dto/DashboardResponse.java`, `dto/DashboardServiceRow.java`, `dto/DashboardRecentCustomer.java` | Dashboard response shapes. |
| `dto/TaxBreakdown.java` | Shared net/tax/gross math both charts and the dashboard build totals on. |
| `service/internal/PlanLimitService` | `assertStatsRangeAllowed` — the gating check both `/api/charts` and `/api/charts/kpi-tiles` call before computing anything. |
| Repositories: `ServiceOrderRepository`, `ExpenseRepository`, `CustomerRepository`, `EmployeeRepository` | Source queries (`findCreatedAtForServiceCount`, `findPaidRevenueLines`, `findPaidRevenueLinesWithPaymentType`, `countByOrgIdAndPaidTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan`, `findJobCountsByEmployee`, `findPaidRevenueLinesByEmployee`, `findUnpaidRevenueLines`, `findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan`). |
