package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.CarBrand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a car brand. {@code orgId} comes from the token. */
public record CarBrandRequest(
        @NotBlank @Size(max = 255) String name
) {
    public void applyTo(CarBrand brand) {
        brand.setName(name.trim());
    }
}
