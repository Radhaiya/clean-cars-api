package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.ServiceCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for a service category. {@code orgId} comes from the token. */
public record ServiceCategoryRequest(
        @NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String name
) {
    public void applyTo(ServiceCategory category) {
        category.setName(name.trim());
    }
}
