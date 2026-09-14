package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Employee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Create/update payload for an employee. {@code orgId} comes from the token. */
public record EmployeeRequest(
        @NotBlank @Size(max = 255) String name
) {
    public void applyTo(Employee employee) {
        employee.setName(name.trim());
    }
}
