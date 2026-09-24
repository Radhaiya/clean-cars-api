# Bikes — UI integration guide

Everything you need to wire the bikes feature into the frontend. The endpoints
mirror the existing **car** endpoints 1:1 — swap `/api/car-*` for `/api/bike-*`
and `carNumber` for `bikeNumber` unless noted below.

Auth on every call: `Authorization: Bearer <token>` (same as all other APIs).
All list endpoints return the standard `PageResponse` envelope:

```json
{
  "content": [ "..." ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1,
  "last": true
}
```

---

## 1. Enum inputs

`fuelType` accepts `PETROL | ELECTRIC | CNG | LPG` **only** (no diesel/hybrid for
bikes). Parsing is case-insensitive (send `petrol` or `PETROL`); unknown values → 400.
Bike models/brands have their own tables and ids — car brand/model ids are **not**
valid on bikes and vice versa.

## 2. Bike brands — `/api/bike-brands`

Full CRUD, same shapes as car brands.

| Verb | Path | Notes |
|---|---|---|
| GET | `/api/bike-brands?search=&page=&size=&sort=name,asc` | paged list, optional `search` by name |
| GET | `/api/bike-brands/{id}` | single |
| POST | `/api/bike-brands` | 201 |
| PUT | `/api/bike-brands/{id}` | |
| DELETE | `/api/bike-brands/{id}` | 409 `bike_brand_in_use` if models still exist |

```json
// POST body
{ "name": "Hero" }

// response (201 / GET / PUT)
{
  "id": "…uuid…",
  "name": "Hero",
  "createdAt": "2026-09-24T10:15:00"
}
```

## 3. Bike models — `/api/bike-models`

Same verbs. List takes an optional `?brandId=` alongside `search`.

```json
// POST/PUT body
{ "brandId": "…bike brand uuid…", "name": "Splendor" }

// response
{ "id": "…", "brandId": "…", "name": "Splendor", "createdAt": "…" }
```

Errors: unknown brand in your org → 404; duplicate name under the same brand →
409 `bike_model_name_exists`.

## 4. Bikes — `/api/bikes`

| Verb | Path | Notes |
|---|---|---|
| GET | `/api/bikes?customerId=&search=&page=&size=&sort=bikeNumber,asc` | filter by customer and/or number |
| GET | `/api/bikes/{id}` | **detail — includes the bike's service history** |
| POST | `/api/bikes` | 201 |
| PUT | `/api/bikes/{id}` | |
| DELETE | `/api/bikes/{id}` | 409 if a service order still references it |

```json
// POST/PUT body — only customerId + bikeNumber are required
{
  "customerId": "…required — the owner…",
  "bikeNumber": "KA01AB1234",          // registration plate
  "brandId": "…optional bike brand…",
  "modelId": "…optional bike model…",
  "year": 2021,                        // optional
  "color": "Black",                    // optional
  "fuelType": "PETROL",                // optional: PETROL | ELECTRIC | CNG | LPG
  "chassisNumber": "…",                // optional
  "engineNumber": "…",                 // optional
  "comments": "…"                      // optional
}
```

```json
// list row (GET /api/bikes) — same fields plus timestamps
{
  "id": "…",
  "customerId": "…",
  "bikeNumber": "KA01AB1234",
  "brandId": "…| null",
  "modelId": "…| null",
  "year": 2021,
  "color": "Black",
  "fuelType": "PETROL",
  "chassisNumber": null,
  "engineNumber": null,
  "comments": null,
  "createdAt": "2026-09-24T10:20:00",
  "updatedAt": "2026-09-24T10:20:00"
}
```

`GET /api/bikes/{id}` (detail) returns all of that **plus** `services` — the
bike's service orders, newest first, same shape as the existing car detail:

```json
"services": [
  {
    "id": "…service order id…",
    "totalAmount": 1180.00,   // gross (paid flag tells you collected or not)
    "paid": false,
    "status": "IN_PROGRESS",
    "employeeId": "…| null",
    "employeeName": "…| null",
    "serviceDate": "2026-09-24T10:45:00"
  }
]
```

Put `/api/bikes/{id}` detail into a drill-in page with a History tab.

**Validation / errors**

- Missing customer/brand/model in your org → `404` (problem detail: `"customer"`, `"brand"`, `"model"` in `title`).
- `400` with field map from bean validation (`errors`) when `bikeNumber` blank/too long, etc.
- Deleting a bike referenced by a service order → `409` from the DB FK.
- `bikeNumber` is **not** unique and never errors (plates get reassigned) — duplicate rows can
  exist; don't treat them as bugs in the UI.

## 5. Customer picker (service-order form) — `/api/customer-bikes`

Mirror of `/api/customer-cars`: a page of customers matched by name, each with
their bikes. Call **both** endpoints when the form supports cars and bikes, or
only this one when adding a bike job.

```
GET /api/customer-bikes?search=&page=&size=&sort=name,asc
```

```json
{
  "content": [
    {
      "customerId": "…",
      "customerName": "Ravi Kumar",
      "phone": "+91…",
      "bikes": [
        { "id": "…bike id…", "bikeNumber": "KA01AB1234", "brand": "Hero", "model": "Splendor" }
      ]
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "last": true
}
```

`brand` / `model` are display names resolved server-side (null if unset).

### 5b. Brand→model cascade — `/api/bike-brand-models`

One call for the "create bike" form (groups every bike model under its brand, both
name-ordered). Mirror of the car `/api/brand-models` endpoint:

```
GET /api/bike-brand-models
```

```json
[
  {
    "brandId": "…",
    "brandName": "Hero",
    "models": [
      { "id": "…", "name": "Splendor" }
    ]
  }
]
```

A brand with no models comes back with `"models": []`. Use it to populate the
brand dropdown, then the model dropdown from the selected brand's `models`
(no extra `/api/bike-models?brandId=` round-trip needed).

## 6. Customer detail now includes bikes

`GET /api/customers/{id}` returns `CustomerVehiclesResponse` — the customer plus
`cars` **and** `bikes` (each entry `{ id, car|bikeNumber, brand, model }`, both
name-sorted):

```json
{
  "id": "…",
  "name": "Ravi Kumar",
  "phone": "+91…",
  "cars": [ { "id": "…", "carNumber": "KA05XY9999", "brand": "Honda", "model": "City" } ],
  "bikes": [ { "id": "…", "bikeNumber": "KA01AB1234", "brand": "Hero", "model": "Splendor" } ]
}
```

For a customer's bikes alone (anywhere else), `GET /api/bikes?customerId={id}` stays right.

## 7. Service orders — open one for a bike

`POST /api/service-orders` body change: send **exactly one** of `carId` / `bikeId`.

```json
{
  "bikeId": "…bike uuid…",        // NEW — or carId for a car (exactly one)
  "employeeId": null,
  "vendorId": null,
  "status": null,                 // omitted → IN_PROGRESS
  "paid": false,
  "items": [ { "serviceName": "…", "basePrice": 999, "gstPercentage": 18, "gstIncluded": false, "quantity": 1 } ]
}
```

- Both/neither set → `400` with a plain message: *"An order belongs to one vehicle — set
  either carId or bikeId, not both"* / *"Either carId or bikeId is required"*.
- Vehicle not in your org → `404`.
- The order's customer is **derived from the vehicle's owner** — no `customerId` in the request.
- On `PUT /api/service-orders/{id}` nothing changes for the vehicle: a job's car/bike is fixed
  forever, so the update payload's vehicle ids are ignored (employee/vendor/status/paid/editable as before).

### Response shape (adds two fields, additive & backward-compatible)

Every `ServiceOrderResponse` and list-row (`ServiceOrderSummaryResponse`) now
carries bike fields next to the car ones; one side is always null:

```json
{
  "id": "…",
  "carId": null,          // null for bike orders
  "carNumber": null,
  "bikeId": "…",          // null for car orders
  "bikeNumber": "KA01AB1234",
  "customerId": "…",
  "customerName": "Ravi Kumar",
  "employeeId": null, "employeeName": null,
  "status": "IN_PROGRESS",
  "paid": false,
  "outsourced": false,
  "vendorId": null, "vendorName": null,
  "itemCount": 2,
  "grossTotal": 1180.00,
  "createdAt": "…", "updatedAt": "…"
}
```

(Full detail responses also add `bikeId`/`bikeNumber` the same way; totals, items,
notes, payment fields are unchanged.) **UI rule:** show `carNumber` when set, else
`bikeNumber`; the list `?search=` now matches customer name, car number **and**
bike number.

`Patch` endpoints (`/{id}/paid`, `/{id}/status`) are vehicle-agnostic — untouched.

## 8. Org totals & quota

```jsonc
// GET /api/charts/totals — one added field
{
  "totalCars": 12,
  "totalBikes": 5,      // NEW
  "totalServices": 18,
  "totalEmployees": 4,
  "totalCustomers": 10
}
```

`GET /api/me` → `plan` gains `currentBikes` (cars stay in `currentCars`). The bike
count shares the plan's `maxCars` cap (soft limit — display-only, no 409 at
create), so one quota bar should weigh `currentCars + currentBikes` against `maxCars`:

```jsonc
"plan": {
  "maxCars": 50,
  "currentCars": 12,
  "currentBikes": 5,    // NEW — shared maxCars cap
  ...
}
```

## 9. Error codes cheat-sheet

| Code / status | When |
|---|---|
| `404` | bike / bike brand / bike model (or referenced customer) not found in the caller's org |
| `409 bike_brand_name_exists` | duplicate bike brand name in org |
| `409 bike_model_name_exists` | duplicate model name under the same bike brand |
| `409 bike_brand_in_use` | deleting a brand that still has models |
| `400` | both/neither of `carId`+`bikeId` on an order; unknown fuel value; bean validation |
| `409` (no code) | deleting a bike/model still referenced by a service order (DB FK) |

All errors are RFC-7807 problem details (`application/problem+json`), same as the rest of the API.
