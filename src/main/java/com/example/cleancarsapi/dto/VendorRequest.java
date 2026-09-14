package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Vendor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a vendor. {@code orgId} comes from the token. */
public record VendorRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String contactPhone,
        @Size(max = 255) String address
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
