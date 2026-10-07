package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Employee;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for an employee. {@code orgId} comes from the token.
 * {@code email} is optional — a bare roster name is a valid employee; the email
 * is only required at invite time ({@code InviteService} rejects the invite when
 * the employee has none).
 */
public record EmployeeRequest(
        @NotBlank @Size(min = FieldLimits.NAME_MIN, max = FieldLimits.NAME_MAX, message = FieldLimits.NAME_MSG) String name,
        @Email @Size(max = FieldLimits.EMAIL_MAX) String email
) {
    public void applyTo(Employee employee) {
        employee.setName(name.trim());
        employee.setEmail(email == null ? null : email.trim().toLowerCase());
    }
}
