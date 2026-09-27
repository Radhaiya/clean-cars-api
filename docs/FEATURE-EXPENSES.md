# Expenses & expense categories

## What it does

A full CRUD resource pair, following the standard four-service pattern
(see `docs/ARCHITECTURE.md`):

- **`ExpenseCategory`** — a pure label dropdown for the caller's org (`name`,
  unique per org). `GET/POST/PUT/DELETE /api/expense-categories`.
- **`Expense`** — one row per expense entry (`categoryName`, `amount`,
  `taxPercentage`, `taxIncluded`, `quantity`, `notes`). `GET/POST/PUT/DELETE
  /api/expenses`. Only these inputs are stored and echoed back — no derived
  `unit*`/`line*` fields on the response; net/tax/gross math (`TaxBreakdown`)
  is computed server-side only, for the charts/KPI totals.

The category on an expense is a **denormalized string, not an FK**
(`expenses.category_name`) — many rows can share a label, and deleting a
category only removes it from the dropdown; historical expenses keep their
name intact. There is no "uncategorized" state — create/update validate that
the name matches one of the org's category labels (case-insensitive, 404 if
not), stopping typos without a real FK. Renaming a category deliberately does
**not** rewrite old expense rows. There is no vendor link and no separate
"expense date" — like `ServiceOrder`, `createdAt` is the only business
timestamp.

`GET /api/expenses` is a flat newest-first list (`?search=` matches
`category_name`/notes). Expense totals feed `totalExpenses`/`totalProfit` on
`GET /api/charts/kpi-tiles` — see `docs/FEATURE-CHARTS-DASHBOARD.md`.

## Files that implement this

| File | Role |
|---|---|
| `entity/Expense.java` | `categoryName`, `amount`, `taxPercentage`, `taxIncluded`, `quantity`, `notes`, `orgId`. |
| `entity/ExpenseCategory.java` | `name`, unique per org. |
| `repository/ExpenseRepository.java` | Org-scoped finders, plus the date-range query used by charts/KPI/dashboard. |
| `repository/ExpenseCategoryRepository.java` | Org-scoped category lookups. |
| `controller/ExpenseController.java` | `GET/POST/PUT/DELETE /api/expenses`. |
| `controller/ExpenseCategoryController.java` | `GET/POST/PUT/DELETE /api/expense-categories`. |
| `service/ExpenseCreateService.java` | Create + `requireCategoryInOrg` (case-insensitive category validation, 404 on typo). |
| `service/ExpenseReadService.java` | Single-get + newest-first list with `?search=`. |
| `service/ExpenseUpdateService.java` | Update (re-validates category on rename). |
| `service/ExpenseDeleteService.java` | Delete by id. |
| `service/ExpenseCategoryCreateService.java`, `ExpenseCategoryReadService.java`, `ExpenseCategoryUpdateService.java`, `ExpenseCategoryDeleteService.java` | Four-service CRUD for the category dropdown. |
| `dto/ExpenseRequest.java`, `dto/ExpenseResponse.java` | Expense DTOs (no derived tax fields — inputs only). |
| `dto/ExpenseCategoryRequest.java`, `dto/ExpenseCategoryResponse.java` | Category DTOs. |
| `dto/TaxBreakdown.java` | Net/tax/gross math consumed by charts/KPI, not exposed on the expense response itself. |
