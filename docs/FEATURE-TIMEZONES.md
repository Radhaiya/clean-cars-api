# Timestamps & timezones — UTC in, org timezone out

## What it does

The JVM and MySQL both operate in UTC internally. Every entity `LocalDateTime`
(`createdAt`, `updatedAt`, etc.) means UTC wall time — no skew between what
Hibernate writes and what it reads back. Each organization carries an IANA
timezone id (`organizations.timezone`, e.g. `Asia/Kolkata`), and API responses
convert UTC → that org's wall time **only at the JSON serialization boundary**,
so business logic never has to think about timezones — it stays in UTC.

- `organizations.timezone` is compulsory, set once at org creation
  (`POST /api/subscription/trial`), validated with `ZoneId.of` (unknown zone → 400).
- `TimezoneJacksonConfig` registers a Jackson `ValueSerializer<LocalDateTime>` that
  writes a wall-time ISO string (`uuuu-MM-dd'T'HH:mm:ss`, no offset). Unauthenticated
  or org-less calls get UTC.
- The org's zone is resolved once per request and memoised in a request attribute
  (`OrgTimeZoneResolver`), sourced from `AuthContext` → `organizations.timezone`.
- `GET /api/reference` exposes a curated pick-list of timezones (one per standard
  UTC offset) for a dropdown; saving still accepts any valid IANA id.
- `LocalDate` fields (trial `startDate`/`endDate`, chart `from`/`to`) carry no zone —
  "day" boundaries are UTC days, not the org's local day.

## Files that implement this

| File | Role |
|---|---|
| `CleanCarsApiApplication.java` | Static block pins the JVM default timezone to UTC for every `main()`-launched run. |
| `config/TimezoneJacksonConfig.java` | Jackson serializer that converts UTC `LocalDateTime` → org wall-time ISO string on the way out. |
| `config/OrgTimeZoneResolver.java` | Resolves and memoises the caller's org timezone once per request. |
| `service/ReferenceDataService.java` | Builds the `timezones` pick-list (`MAIN_ZONES`, one per standard offset) returned by `GET /api/reference`. |
| `controller/ReferenceDataController.java` | Exposes `GET /api/reference` (permit-all). |
| `service/SubscriptionService.java` | Validates and persists `timezone` when a trial/org is created (`ZoneId.of` check). |
| `service/UserService.java` | Surfaces `orgTimezone` on `GET /api/me`. |
| `service/DashboardService.java` | Consumes the org timezone when bucketing "today"/"yesterday" figures. |
| `dto/OrganizationUpdateRequest.java` | Carries `timezone` on `PUT /api/organization` (full-replace convention). |
| `dto/UserProfile.java` | `orgTimezone` field returned by `GET /api/me`. |
| `dto/internal/ConsoleTimes.java` | Internal-console projection that also needs UTC↔org-timezone conversion. |

`build.gradle` sets `-Duser.timezone=UTC` on the Gradle `test` task, since
`@SpringBootTest` skips `main()` and would otherwise inherit the host's timezone.
