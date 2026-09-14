package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.CarModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create/update payload for a car model. {@code orgId} comes from the token. */
public record CarModelRequest(
        @NotNull Long brandId,
        @NotBlank @Size(max = 255) String name
) {
    public void applyTo(CarModel model) {
        model.setBrandId(brandId);
        model.setName(name.trim());
    }
}
