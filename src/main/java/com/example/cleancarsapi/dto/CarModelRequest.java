package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.CarModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
/** Create/update payload for a car model. {@code orgId} comes from the token. */
public record CarModelRequest(
        @NotNull UUID brandId,
        @NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String name
) {
    public void applyTo(CarModel model) {
        model.setBrandId(brandId);
        model.setName(name.trim());
    }
}
