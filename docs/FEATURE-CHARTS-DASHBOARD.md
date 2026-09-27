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
endpoint.

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
| `dto/KpiTilesResponse.java` | `{ totalRevenue, totalExpenses, totalProfit }`. |
| `controller/DashboardController.java` | `GET /api/dashboard`. |
| `service/DashboardService.java` | Builds the dashboard snapshot; depends on `ServiceOrderRepository`/`ExpenseRepository`, and the org-timezone resolver for "today"/"yesterday" boundaries. |
| `dto/DashboardResponse.java`, `dto/DashboardServiceRow.java`, `dto/DashboardRecentCustomer.java` | Dashboard response shapes. |
| `dto/TaxBreakdown.java` | Shared net/tax/gross math both charts and the dashboard build totals on. |
| `service/internal/PlanLimitService` | `assertStatsRangeAllowed` — the gating check both `/api/charts` and `/api/charts/kpi-tiles` call before computing anything. |
| Repositories: `ServiceOrderRepository`, `ExpenseRepository` | Source queries (`findCreatedAtForServiceCount`, `findPaidRevenueLines`, `findUnpaidRevenueLines`, `findByOrgIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan`). |
