package com.example.cleancarsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Update payload for an AMC plan — only the name is editable (the service names are fixed). */
public record AmcPlanRenameRequest(@NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String name) {
}
