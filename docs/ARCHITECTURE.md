# clean-cars-api — Architecture & Conventions

Spring Boot 4 · Java 25 · Spring Data JPA (Hibernate) · MySQL 8 · stateless JWT auth.

## Package layout (by layer)

```
com.example.cleancarsapi
├── controller/    REST endpoints. Thin: read AuthContext, call services, return DTOs.
├── service/       Business logic. One service per CRUD operation (see below).
├── repository/    Spring Data JPA interfaces. All finders are org-scoped.
├── entity/        @Entity classes + JPA converters. camelCase fields → snake_case columns.
├── dto/           Request/response records. Never expose entities on write paths.
├── security/      AuthContext, AuthenticatedUser, JWT → principal converter.
├── config/        SecurityConfig (filter chain, JwtEncoder/Decoder, PasswordEncoder).
└── exception/     Domain exceptions + @RestControllerAdvice (RFC 7807 responses).
```

## CRUD = four services per resource

**Rule:** every resource that exposes full CRUD gets **four separate `@Service` classes**,
one per operation. Never a single fat `XService`.

| Service              | Methods                     | Transaction              |
|----------------------|-----------------------------|--------------------------|
| `XCreateService`     | `create(orgId, XRequest)`   | `@Transactional`         |
| `XReadService`       | `get(orgId, id)`, `list(orgId, search, Pageable)` | `@Transactional(readOnly = true)` |
| `XUpdateService`     | `update(orgId, id, XRequest)`| `@Transactional`        |
| `XDeleteService`     | `delete(orgId, id)`         | `@Transactional`         |

- **Read owns both** single fetch and paged list (the "R" in CRUD, not a 5th service).
- The controller injects all four and delegates one line per endpoint.
- Field-copy logic shared by create/update lives on the request DTO as `applyTo(entity)`,
  not in a shared service.
- Read-only support services that are **not** CRUD resources (e.g. `UserService.getProfile`,
  `OrganizationService.getById`) stay as a single class.

Reference implementation: `customer` → `CustomerCreateService`, `CustomerReadService`,
`CustomerUpdateService`, `CustomerDeleteService`, wired by `CustomerController`.
Same shape: `car-brand` (`/api/car-brands`), `car-model` (`/api/car-models`,
list filterable by `?brandId=`), `car` (`/api/cars`, list filterable by
`?customerId=` + `?search=` on the plate). A brand with models can't be deleted
(`car_brand_in_use` → 409); a model's `brandId` must resolve within the caller's org.
For a car only `customerId` + `carNumber` are required; any supplied
`customerId`/`brandId`/`modelId` is checked against the org (`CarReferenceValidator`,
404 if it doesn't resolve). `fuelType` is a `FuelType` enum stored lowercase
(`FuelTypeConverter`), accepted case-insensitively in JSON.

Minimal same-shape resources, org-scoped, `?search=` on the list, no uniqueness rule:
`employee` (`/api/employees`) — just a `name`; `vendor` (`/api/vendors`) — an outside
garage (`name` + optional `contactPhone` / `address`).

`service-order` (`/api/service-orders`) — "the service log", full CRUD in the four-service
shape. Parent + snapshot lines:
- `carId` is the only required field; `customerId` is **derived from the car's owner** and,
  with `carId`, is fixed after create. `createdBy` = the token's user.
- `employeeId` (→ `employees`) is the staff member on the job. There is no outsourced flag —
  a non-null `vendorId` (→ `vendors`) sends the whole job out and marks it outsourced; the
  response exposes a derived `outsourced` boolean.
- `status` (`in_progress`/`completed`/`cancelled`) is a lowercase-DB enum; null on create →
  `in_progress`. `?status=` accepts either case. Reaching `completed` stamps `completedAt`;
  moving away from it clears it.
- `paid` (boolean), `paymentDate`, and `paymentType` (`card`/`cash`/`upi`, lowercase-DB enum,
  null until recorded) are all **independent** — set any without touching the others.
- **No stored total.** `service_order_items` stores `base_price` + `gst_percentage` +
  `gst_included` + `quantity` per line (a catalog `serviceCatalogId` in the request is read
  once to *seed* those and is never persisted — nothing links a line back to the catalog).
  All GST math (`GstBreakdown`, shared with `service-catalog`) and the order total are
  computed on read. `items` in the request replaces the whole line set.
- List returns lightweight `ServiceOrderSummaryResponse` (names + gross total, no lines);
  `GET /{id}` returns the lines and the net/GST/gross totals.
- Quick edits (no full body): `PATCH /{id}/paid` `{"paid": true}` flips paid without touching
  `paymentDate` / `paymentType`; `PATCH /{id}/status` `{"status": "completed"}` transitions the
  status (stamps/clears `completedAt`). Both return the full `ServiceOrderResponse`.

`GET /api/customer-cars` — read-only lookup (single service): a page of customers matched by
name, each with their cars (`carId` + car number + brand/model), to populate the
service-order form.

Same shape again: `service-catalog` (`/api/service-catalog`) — the org's price list.
Only the **base price** (`price`) plus the GST inputs (`gstPercentage` nullable,
`gstIncluded` boolean) are stored; a final/net/gross price is **never persisted** —
`ServiceCatalogResponse.from(entry, categoryName)` derives `netAmount` / `gstAmount` /
`grossAmount` on every read (`gstIncluded=true` → stored price is gross; `false` → net;
null/zero rate → no GST). Unique per `(org_id, name)` → `service_catalog_name_exists` 409.
Optional `categoryId` → `service_categories` (`ServiceCategoryLookup` validates it belongs
to the org, 404 if not); the response carries `categoryId` + resolved `categoryName`
(batch-loaded for the list, single lookup for get).

`service-category` (`/api/service-categories`) — same four-service shape as `car-brand`:
an org's own groupings for the catalogue (`name` only), unique per `(org_id, name)` →
`service_category_name_exists` 409. Deleting a category **detaches** its services
(`service_catalog.category_id` FK is `ON DELETE SET NULL`) rather than blocking.

`GET /api/brand-models` — read-only lookup (single service, no CRUD split):
the org's brands each with their models nested, for populating the create-car form.

`GET /api/customers/{id}` returns `CustomerAndCarsResponse` — the customer plus a
`cars` array (`id`, `carNumber`, `brand` name, `model` name; brand/model omitted when
null). List and update responses stay the plain `CustomerResponse`. The cars are
fetched with one entity-join query (`CarRepository.findSummariesByCustomer`,
`left join CarBrand … left join CarModel …`).

`GET /api/cars/{id}` returns `CarAndServicesResponse` — the car plus a `services` array
(`CarServiceSummary`: order `id`, `totalAmount` = computed gross total, `paid`, `status`,
`employeeId`/`employeeName`, `serviceDate` = order `createdAt`), **all** the car's service
orders newest-first (`ServiceOrderAssembler.historyForCar`). List/create/update stay the
plain `CarResponse`.

## Multi-tenancy

- Every business row carries `orgId`. It is set **once** on create from the token and is
  `updatable = false`.
- The org id always comes from `AuthContext.requireOrgId()` — never from the request body,
  path, or query string.
- Repository finders are always org-scoped: `findByIdAndOrgId(...)`, `search(orgId, ...)`.
  A lookup that misses returns `NotFoundException` (404) — a caller must not be able to tell
  "wrong org" from "doesn't exist".

## Tokens

**Access token** — HS256 JWT, self-contained, signed with `app.jwt.secret`.
Claims: `iss`, `iat`, `exp`, `sub`(userId), `email`, `name`, `role`, `org_id`.
Lifetime `app.jwt.ttl-seconds` (local 3600, prod 900). Verified by the resource
server on every protected call; never stored server-side.

**Refresh token** — opaque 256-bit random string, returned once at login/refresh.
Only its SHA-256 hash is stored (`refresh_tokens` table). Lifetime
`app.jwt.refresh-ttl-days` (default 30).

| Endpoint (all under permit-all `/api/auth/**`) | Behaviour |
|---|---|
| `POST /api/auth/login`   | credentials → `{ token, tokenType, expiresIn, refreshToken }` |
| `POST /api/auth/refresh` | `{ refreshToken }` → **rotates**: revokes the presented token, issues a new access + refresh pair |
| `POST /api/auth/logout`  | `{ refreshToken }` → revokes it (idempotent → 204) |

- **Rotation:** every refresh invalidates the old refresh token.
- **Reuse detection:** presenting an already-revoked refresh token revokes *every*
  active refresh token for that user (`TokenReuseException` + `noRollbackFor` so the
  lockout write survives the 401).
- `AuthService` holds no `@Transactional` — each step delegates to an already
  transactional collaborator, so the lockout can't be rolled back by an outer tx.
- Invalid/expired/unknown refresh token → 401; missing field → 400.

## AuthContext

`AuthContext` is a static accessor for the current principal, usable in any layer:

```java
AuthenticatedUser me = AuthContext.require();          // userId, orgId, role, email, name
long orgId           = AuthContext.requireOrgId();     // 403 if the user has no org
AuthContext.require(UserRole.ADMIN);                    // 403 if role doesn't match
```

It is populated by `AuthenticatedUserJwtConverter` from the JWT `sub` / `org_id` / `role`
claims, so no extra DB hit and no parameter threading.

## DTOs

- `XRequest` — validated input (`jakarta.validation`), carries `applyTo(entity)`.
- `XResponse` — output projection with a static `from(entity)`.
- `PageResponse<T>` — stable pagination envelope (`content`, `page`, `size`,
  `totalElements`, `totalPages`, `last`); we do not serialize Spring Data `Page` directly.
- DTOs are Java `record`s — no Lombok needed.

## Lombok

- **Entities:** `@Getter` (+ `@Setter` only where the entity is mutated, i.e. `Customer`),
  `@NoArgsConstructor` for Hibernate (`PROTECTED` for read-only `User`/`Organization`).
  Never `@Data`/`@ToString`/`@EqualsAndHashCode` on an `@Entity`.
- **Services & controllers:** `@RequiredArgsConstructor` over `private final` fields —
  no hand-written constructors. `@Slf4j` where logging is needed.
- **Not** used where a constructor does real work (`SecurityConfig`, `JwtService` build
  the signing key from `@Value`s) or on records.
- Root `lombok.config` sets `config.stopBubbling = true` and
  `lombok.addLombokGeneratedAnnotation = true` (keeps generated code out of coverage).

## Errors

Throw a domain exception; `ApiExceptionHandler` maps it:

| Exception                          | Status | Body |
|------------------------------------|--------|------|
| `BadCredentialsException`          | 401    | ProblemDetail |
| `DisabledException`, `ForbiddenException` | 403 | ProblemDetail |
| `NotFoundException`                | 404    | ProblemDetail |
| `ConflictException`                | 409    | ProblemDetail + `code` (e.g. `customer_phone_exists`) |
| `DataIntegrityViolationException`  | 409    | ProblemDetail (race-condition safety net) |
| `MethodArgumentNotValidException`  | 400    | ProblemDetail + `errors` map |

Uniqueness (e.g. customer phone, unique per org) is pre-checked in the service for a
clean `409 { code }` the UI can branch on, and backed by a DB unique constraint
(`uq_customers_org_phone`) that maps to `409` if a race slips through.

## Config

`application.yml` (common) + `application-{local,stage,prod}.yml` (main) +
`application-test.yml` (test). Default profile `local`; `SPRING_PROFILES_ACTIVE` overrides.
`stage`/`prod` read `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` from the
environment. Schema is owned by `src/main/resources/db/schema.sql` (loaded by
`docker-compose.yml`), never by Hibernate (`ddl-auto: none`).
