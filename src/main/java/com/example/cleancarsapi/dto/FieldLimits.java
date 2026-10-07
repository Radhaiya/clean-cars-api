package com.example.cleancarsapi.dto;

/**
 * Text-field limits shared by the request DTOs. The UI mirrors every one of these in
 * {@code clean-cars-ui/src/lib/limits.ts} — change both together, or the app starts getting 400s
 * for input its own forms accepted.
 */
public final class FieldLimits {
    public static final int NAME_MIN = 2;
    public static final int NAME_MAX = 60;
    public static final String NAME_MSG = "Name must be 2–60 characters";

    public static final int EMAIL_MAX = 100;
    public static final int ADDRESS_MAX = 200;
    public static final int NOTES_MAX = 500;

    /** Car / bike number: letters, digits, spaces, hyphens; 4–15 letters+digits once spaces and hyphens are ignored. */
    public static final int VEHICLE_NUMBER_MAX_CHARS = 20;
    public static final String VEHICLE_NUMBER_RE =
            "^(?=(?:[^A-Za-z0-9]*[A-Za-z0-9]){4,15}[^A-Za-z0-9]*$)[A-Za-z0-9 \\-]{4,20}$";
    public static final String VEHICLE_NUMBER_MSG =
            "Number must be 4–15 letters or digits (spaces and hyphens allowed)";

    public static final int ORG_NAME_MAX = 100;
    public static final String ORG_NAME_MSG = "Organisation name must be 2–100 characters";
    public static final int TAGLINE_MAX = 100;
    public static final int ADDRESS_LINE_MAX = 100;
    public static final int STATE_MAX = 60;
    public static final int ZIP_MAX = 12;
    public static final String ZIP_RE = "^[A-Za-z0-9 \\-]*$";
    public static final String ZIP_MSG = "ZIP / postal code must be at most 12 letters, digits, spaces or hyphens";

    public static final int PHONE_MIN = 7;
    public static final int PHONE_MAX = 25;
    /** Digits plus the usual separators; the per-country digit count is checked in the UI. */
    public static final String PHONE_RE = "^[0-9+()\\-\\s]*$";
    public static final String PHONE_MSG = "Phone must be 7–25 characters: digits, spaces, + ( ) -";

    private FieldLimits() {
    }
}
