package com.example.cleancarsapi.service;

import com.example.cleancarsapi.dto.ReferenceDataResponse.CountryCodeOption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * ISO 3166-1 alpha-2 → international calling code, the data behind the org's phone
 * country code. The JDK has no dial-code data, so it is a static table; country names
 * come from the JDK locale data and the flag is the regional-indicator emoji derived
 * from the ISO code. Where several countries share a code (+1, +7, +44 …) the primary
 * country is listed first so a dial-code lookup resolves to it.
 */
public final class CountryDialCodes {

    private static final String RAW = """
            US:1 CA:1 AS:1684 AI:1264 AG:1268 BS:1242 BB:1246 BM:1441 VG:1284 KY:1345 DM:1767 DO:1809 GD:1473 \
            GU:1671 JM:1876 MS:1664 MP:1670 PR:1787 KN:1869 LC:1758 VC:1784 SX:1721 TT:1868 TC:1649 VI:1340 \
            RU:7 KZ:7 EG:20 ZA:27 GR:30 NL:31 BE:32 FR:33 ES:34 HU:36 IT:39 RO:40 CH:41 AT:43 GB:44 GG:44 \
            IM:44 JE:44 DK:45 SE:46 NO:47 PL:48 DE:49 PE:51 MX:52 CU:53 AR:54 BR:55 CL:56 CO:57 VE:58 MY:60 \
            AU:61 ID:62 PH:63 NZ:64 SG:65 TH:66 JP:81 KR:82 VN:84 CN:86 TR:90 IN:91 PK:92 AF:93 LK:94 MM:95 \
            IR:98 SS:211 MA:212 DZ:213 TN:216 LY:218 GM:220 SN:221 MR:222 ML:223 GN:224 CI:225 BF:226 NE:227 \
            TG:228 BJ:229 MU:230 LR:231 SL:232 GH:233 NG:234 TD:235 CF:236 CM:237 CV:238 ST:239 GQ:240 GA:241 \
            CG:242 CD:243 AO:244 GW:245 SC:248 SD:249 RW:250 ET:251 SO:252 DJ:253 KE:254 TZ:255 UG:256 BI:257 \
            MZ:258 ZM:260 MG:261 ZW:263 NA:264 MW:265 LS:266 BW:267 SZ:268 KM:269 ER:291 AW:297 FO:298 GL:299 \
            GI:350 PT:351 LU:352 IE:353 IS:354 AL:355 MT:356 CY:357 FI:358 BG:359 LT:370 LV:371 EE:372 MD:373 \
            AM:374 BY:375 AD:376 MC:377 SM:378 UA:380 RS:381 ME:382 XK:383 HR:385 SI:386 BA:387 MK:389 CZ:420 \
            SK:421 LI:423 FK:500 BZ:501 GT:502 SV:503 HN:504 NI:505 CR:506 PA:507 HT:509 BO:591 GY:592 EC:593 \
            PY:595 SR:597 UY:598 CW:599 TL:670 BN:673 PG:675 TO:676 VU:678 FJ:679 PW:680 WS:685 KI:686 NC:687 \
            PF:689 CK:682 NU:683 FM:691 MH:692 KP:850 HK:852 MO:853 KH:855 LA:856 BD:880 TW:886 MV:960 LB:961 \
            JO:962 SY:963 IQ:964 KW:965 SA:966 YE:967 OM:968 PS:970 AE:971 IL:972 BH:973 QA:974 BT:975 MN:976 \
            NP:977 TJ:992 TM:993 AZ:994 GE:995 KG:996 UZ:998
            """;

    /** National (significant) number length per country, {@code ISO:min-max} or {@code ISO:exact}; others fall back to E.164. */
    private static final String LENGTHS = """
            IN:10 US:10 CA:10 GB:9-10 AE:9 SA:9 PK:10 BD:10 LK:9 NP:10 AU:9 NZ:8-10 SG:8 MY:9-10 DE:10-11 \
            FR:9 IT:9-10 ES:9 ID:9-12 PH:10 ZA:9 NG:10 KE:9 QA:8 KW:8 OM:8 BH:8 JP:10 CN:11 KR:9-10 BR:10-11 \
            MX:10 RU:10 TR:10 EG:10 TH:9 VN:9-10 NL:9
            """;

    /** ISO → {min, max} national-number digits. */
    private static final Map<String, int[]> LENGTH_BY_ISO = new LinkedHashMap<>();

    /** ISO → dial code with a leading {@code +}, in table order (primary country of a shared code first). */
    private static final Map<String, String> DIAL_BY_ISO = new LinkedHashMap<>();

    private static final List<CountryCodeOption> OPTIONS;

    static {
        for (String pair : RAW.trim().split("\\s+")) {
            String[] parts = pair.split(":");
            DIAL_BY_ISO.put(parts[0], "+" + parts[1]);
        }
        for (String pair : LENGTHS.trim().split("\\s+")) {
            String[] parts = pair.split(":");
            String[] range = parts[1].split("-");
            int min = Integer.parseInt(range[0]);
            LENGTH_BY_ISO.put(parts[0], new int[] {min, Integer.parseInt(range[range.length - 1])});
        }
        List<CountryCodeOption> options = new ArrayList<>();
        DIAL_BY_ISO.forEach((iso, dial) -> {
            String name = Locale.of("", iso).getDisplayCountry(Locale.ENGLISH);
            if (name.isBlank() || name.equals(iso)) {
                return; // not a country the JDK knows — leave it out of the pick-list
            }
            String flag = flagOf(iso);
            options.add(new CountryCodeOption(iso, name, dial, flag, flag + " " + name + " (" + dial + ")"));
        });
        options.sort(Comparator.comparing(CountryCodeOption::name));
        OPTIONS = List.copyOf(options);
    }

    private CountryDialCodes() {
    }

    public static List<CountryCodeOption> options() {
        return OPTIONS;
    }

    /** The listed option for an ISO code (case-insensitive), if any. */
    public static Optional<CountryCodeOption> find(String iso) {
        if (iso == null) {
            return Optional.empty();
        }
        String normalized = iso.trim().toUpperCase(Locale.ROOT);
        return OPTIONS.stream().filter(o -> o.isoCode().equals(normalized)).findFirst();
    }

    /** Longest-prefix match of an E.164 number ({@code +9198…}) to its primary country's ISO code. */
    public static Optional<String> isoForPhone(String phone) {
        if (phone == null || !phone.trim().startsWith("+")) {
            return Optional.empty();
        }
        String number = phone.trim();
        String best = null;
        String bestDial = "";
        for (Map.Entry<String, String> e : DIAL_BY_ISO.entrySet()) {
            String dial = e.getValue();
            if (number.startsWith(dial) && dial.length() > bestDial.length()) {
                best = e.getKey();
                bestDial = dial;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The country whose only currency is {@code currencyCode}, if exactly one — else empty (EUR, USD, … are shared). */
    public static Optional<String> isoForCurrency(String currencyCode) {
        if (currencyCode == null) {
            return Optional.empty();
        }
        List<String> matches = DIAL_BY_ISO.keySet().stream()
                .filter(iso -> {
                    try {
                        return Currency.getInstance(Locale.of("", iso)).getCurrencyCode().equals(currencyCode);
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    /** Allowed national-number digit count {min, max} for a country; E.164 (15 digits incl. dial code) when unlisted. */
    public static int[] nationalLength(String iso, String dialCode) {
        int[] known = iso == null ? null : LENGTH_BY_ISO.get(iso.trim().toUpperCase(Locale.ROOT));
        if (known != null) {
            return known;
        }
        int dialDigits = dialCode == null ? 0 : dialCode.replaceAll("\\D", "").length();
        // NANP territories (+1 242, +1 876 …): 10-digit numbers including the area code.
        if (dialDigits > 1 && dialCode.startsWith("+1")) {
            int national = 11 - dialDigits;
            return new int[] {national, national};
        }
        return new int[] {4, Math.max(4, 15 - dialDigits)};
    }

    /**
     * Error message if {@code phone} has the wrong digit count for the country, else null. A leading copy of
     * the org's dial code is ignored; a number with some other {@code +} prefix only gets the E.164 cap.
     */
    public static String phoneLengthError(String iso, String dialCode, String phone) {
        String value = phone == null ? "" : phone.trim();
        if (value.startsWith("+") && (dialCode == null || !value.startsWith(dialCode))) {
            return value.replaceAll("\\D", "").length() <= 15 ? null : "Phone number is too long";
        }
        if (dialCode != null && value.startsWith(dialCode)) {
            value = value.substring(dialCode.length());
        }
        if (!value.matches("[\\d\\s()-]+")) {
            return "Phone number may only contain digits";
        }
        int digits = value.replaceAll("\\D", "").length();
        int[] range = nationalLength(iso, dialCode);
        if (digits >= range[0] && digits <= range[1]) {
            return null;
        }
        String expected = range[0] == range[1] ? String.valueOf(range[0]) : range[0] + "-" + range[1];
        return "Phone number must be " + expected + " digits for " + (dialCode == null ? "this country" : dialCode);
    }

    private static String flagOf(String iso) {
        StringBuilder sb = new StringBuilder();
        for (char c : iso.toCharArray()) {
            sb.appendCodePoint(0x1F1E6 + (c - 'A'));
        }
        return sb.toString();
    }
}
