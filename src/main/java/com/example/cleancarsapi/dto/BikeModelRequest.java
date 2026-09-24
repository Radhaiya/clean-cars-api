package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.BikeModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
/** Create/update payload for a bike model. {@code orgId} comes from the token. */
public record BikeModelRequest(
        @NotNull UUID brandId,
        @NotBlank @Size(max = 255) String name
) {
    public void applyTo(BikeModel model) {
        model.setBrandId(brandId);
        model.setName(name.trim());
    }
}
