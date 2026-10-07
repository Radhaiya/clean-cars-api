package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.BikeBrand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a bike brand. {@code orgId} comes from the token. */
public record BikeBrandRequest(
        @NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String name
) {
    public void applyTo(BikeBrand brand) {
        brand.setName(name.trim());
    }
}
