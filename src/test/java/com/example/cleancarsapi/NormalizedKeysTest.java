package com.example.cleancarsapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.cleancarsapi.dto.FieldLimits;
import com.example.cleancarsapi.service.NormalizedKeys;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class NormalizedKeysTest {

    @Test
    void vehicleNumbersMatchIgnoringSpacesHyphensAndCase() {
        assertEquals("MH13AB1234", NormalizedKeys.vehicle("MH 13 AB 1234"));
        assertEquals(NormalizedKeys.vehicle("mh-13-ab-1234"), NormalizedKeys.vehicle("MH13AB1234"));
    }

    @Test
    void phonesMatchIgnoringFormatting() {
        assertEquals("919876543210", NormalizedKeys.phone("+91 98765-43210"));
        assertEquals(NormalizedKeys.phone("(98765) 43210"), NormalizedKeys.phone("9876543210"));
    }

    @Test
    void displayFormKeepsSingleSpacesAndUppercases() {
        assertEquals("MH 13 AB 1234", NormalizedKeys.vehicleDisplay("  mh   13 ab 1234 "));
    }

    @Test
    void vehicleNumberPatternCountsOnlyLettersAndDigits() {
        Pattern p = Pattern.compile(FieldLimits.VEHICLE_NUMBER_RE);
        assertTrue(p.matcher("MH 13 AB 1234").matches());
        assertTrue(p.matcher("MH13AB1234").matches());
        assertTrue(p.matcher("MH-13-AB-1234").matches());
        assertFalse(p.matcher("AB 1").matches());          // too short
        assertFalse(p.matcher("MH13AB1234567890X").matches()); // 17 letters/digits
        assertFalse(p.matcher("MH13#AB1234").matches());    // symbol
    }
}
