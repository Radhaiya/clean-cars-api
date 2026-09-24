package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.BikeBrand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a bike brand. {@code orgId} comes from the token. */
public record BikeBrandRequest(
        @NotBlank @Size(max = 255) String name
) {
    public void applyTo(BikeBrand brand) {
        brand.setName(name.trim());
    }
}
