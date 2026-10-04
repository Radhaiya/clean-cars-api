# Organization phone country code

One country code per org is the single source of truth for the prefix on every phone
number. **Display-only**: stored phones (customers, vendors, org contact) are never
rewritten; the UI renders `dialCode + number`, and a stored number that already starts
with `+`/`00` is shown as-is (no double prefix). Changing the org's code changes every
screen at once.

- **Columns** — `organizations.phone_country_iso` (`CHAR(2)`, drives the flag; `+1` is
  shared) and `phone_dial_code` (`VARCHAR(8)`, `+91`), both **nullable** — migration
  `021-org-phone-country-code.sql`. Existing orgs stay `NULL` and keep working.
- **Reference** — `GET /api/reference` → `countryCodes`:
  `{isoCode, name, dialCode, flag (emoji), label: "🇮🇳 India (+91)"}` (`CountryDialCodes`).
  The client sends back only `isoCode`; the dial code is resolved server-side.
- **Onboarding** — `countryCode` is `@NotBlank` on `POST /api/subscription/trial`.
- **Existing orgs** — `PUT /api/organization` accepts `countryCode` (null = unchanged, unlike
  the other full-replace fields); `PUT /api/organization/country-code` sets only it (owner/admin).
- **Enforcement** — `POST /api/customers` → 409 `country_code_required` until the org has one.
- **Nudge for existing orgs** — `GET /api/me` returns `orgPhoneCountryIso`, `orgPhoneDialCode`
  and `suggestedPhoneCountryIso` (owner's OTP-verified phone prefix, else a currency that maps
  to exactly one country; null otherwise). The UI shows a "!" on Profile, a dashboard banner
  with one-tap "Use 🇮🇳 India (+91)", and blocks add-customer until saved.

| File | Role |
|---|---|
| `service/CountryDialCodes.java` | ISO → calling-code table, option list, phone/currency → ISO guesses. |
| `service/ReferenceDataService.java` | `requireCountryCode`, adds `countryCodes` to the reference. |
| `service/OrganizationService.java`, `controller/OrganizationController.java` | `PUT /api/organization/country-code`. |
| `service/CustomerCreateService.java` | `country_code_required` gate. |
| `service/UserService.java`, `dto/UserProfile.java` | `me` fields + suggestion. |
