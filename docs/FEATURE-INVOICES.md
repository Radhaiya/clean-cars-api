# Feature: Invoices

The printable tax invoice of a service order. The API stores only what the order doesn't hold; the client builds the A4 document from the order + the organization (`GET /api/organization`) + this record.

## Model — `invoices` (migration `025-invoices.sql`, entity `Invoice`)

| Column | Notes |
|--------|-------|
| `service_order_id` | unique — at most one invoice per order; FK to `service_orders` |
| `invoice_number` | per-org running sequence from 1, unique with `org_id`; assigned once, never reused while the invoice exists |
| `invoice_date` | issue date; defaults to today in the org's timezone |
| `next_service_date`, `next_service_km` | optional hints printed on the invoice |
| `notes` | optional, ≤ 1000 chars, printed under *Terms and Conditions* |

Lines, totals, discounts, tax and payments are **not** copied — they are read live from the order.

## Endpoints — `InvoiceController` (`/api/service-orders/{orderId}/invoice`, org-scoped via the token)

| Method | Behaviour |
|--------|-----------|
| `GET` | the invoice, or `404` until it is created |
| `POST` | create (`201`). `409 invoice_exists` if the order already has one; an AMC redemption (₹0, 100% discount lines) can be invoiced like any order. Locks the org row so numbering and double submits are serialised |
| `PUT` | edit date / hints / notes — an omitted `invoiceDate` is kept, omitted hints and notes clear |

Body (`InvoiceRequest`): `{ invoiceDate?, nextServiceDate?, nextServiceKm?, notes? }`. Response (`InvoiceResponse`): `{ id, serviceOrderId, invoiceNumber, invoiceDate, nextServiceDate, nextServiceKm, notes, createdAt }`.

Deleting a service order deletes its invoice (`ServiceOrderDeleteService`).

Code: `InvoiceService`, `InvoiceRepository`, tests in `InvoiceTest`. UI: see `clean-cars-ui/docs/features/service-order.md` → "Invoice".

## Org invoice look — migration `026-org-invoice-settings.sql`

`organizations.invoice_template` (`CLASSIC | MODERN | BOLD | MINIMAL`, `InvoiceTemplate`) and `invoice_color` (`#RRGGBB`, stored upper-case); both null until the owner chooses. `PUT /api/organization/invoice-settings` `{ template, color }` — owner only (`403` otherwise), `400` for a bad colour; changes nothing else on the org. Both fields come back on `GET /api/organization`. The UI draws the templates.

## AMC sale invoices — migration `027-amc-invoices.sql` (`AmcInvoice`, `AmcInvoiceService`, `AmcInvoiceController`)

One invoice per sold AMC, for the upfront sale. Numbered per org on its **own** sequence (UI prints `AMC-0001…`, separate from the service invoices' `INV-` numbers), assigned under the same org-row lock; unique per `(org_id, invoice_number)`.

`/api/amc-subscriptions/{id}/invoice` — `GET` (404 until created), `POST` (201; `409 invoice_exists`), `PUT` (date / note). Body `{ invoiceDate?, notes? }`. Plan must include AMC (`assertAmcEnabled`). The response adds the bill-to the AMC lacks — `customerName`, `customerPhone` (the vehicle's owner) and `vehicle` — resolved even for soft-deleted vehicles / owners; plan, rows and sale totals are read from `GET /api/amc-subscriptions/{id}`. Tests: `AmcSaleTest.amcInvoicesRunTheirOwnPerOrgSequenceAndBillTheVehicleOwner`.
