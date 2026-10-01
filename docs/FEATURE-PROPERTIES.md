# Feature properties — runtime switches

`feature_properties` (migration `013-feature-properties.sql`) holds key → JSON value
switches the internal console flips and the apps read. The value is a JSON document,
so a property can be a boolean, string, number or object without a schema change.

## Seeded keys (both `false`)

| Key | Effect when `true` |
|---|---|
| `Client.Maintenance.Mode.Enable` | UI shows a full-screen *Maintenance Mode* page; every tenant `/api/**` call answers `503` `code=maintenance_mode` |
| `Client.New.Logins.Disabled` | `POST /api/auth/firebase` for a Firebase identity with no existing `users` row → `403` `code=new_logins_disabled`. Existing users (by `firebase_uid` or email) still sign in. UI shows a notice and dims the Google button (still pressable) |

## API

- `GET /api/properties` — **permit-all**, returns only `Client.*` keys as `{key: value}`. The main UI polls it every 30 s.
- `GET /internal/api/properties` — console, all rows (`id, key, value, description, updatedByEmail, updatedAt`).
- `PUT /internal/api/properties/{key}` `{value, description?}` — create-or-update. An existing property must keep its JSON type, else `409 property_type_mismatch`. Key: `[A-Za-z0-9_.-]{1,128}`.

## Files

`FeaturePropertyService` (5 s cache, cleared on write), `FeaturePropertyController`, `InternalFeaturePropertyController`,
`MaintenanceModeInterceptor` + `WebConfig` (exempts `/api/properties`, `/api/auth/refresh`, `/api/auth/logout`, `/api/webhooks/razorpay`, `/api/reference`),
`AuthService.provisionNewUser`, `ForbiddenException.newLoginsDisabled`, `ServiceUnavailableException`.
Tests: `FeaturePropertyServiceTest`, `FeaturePropertiesWebTest`.
Other UIs: `internal-mygarageone` → Properties tab; `clean-cars-ui` → `slices/properties`, `MaintenanceScreen`, `LoginScreen`.
