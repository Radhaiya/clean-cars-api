package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.Pattern;
import com.example.cleancarsapi.entity.Vendor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a vendor. {@code orgId} comes from the token. */
public record VendorRequest(
        @NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String name,
        @Size(max = FieldLimits.PHONE_MAX, message = FieldLimits.PHONE_MSG) @Pattern(regexp = FieldLimits.PHONE_RE, message = FieldLimits.PHONE_MSG) String contactPhone,
        @Size(max = FieldLimits.ADDRESS_MAX) String address
) {
    public void applyTo(Vendor vendor) {
        vendor.setName(name.trim());
        vendor.setContactPhone(blankToNull(contactPhone));
        vendor.setAddress(blankToNull(address));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
