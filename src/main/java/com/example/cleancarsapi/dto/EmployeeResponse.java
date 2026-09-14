package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Employee;

import java.time.LocalDateTime;

public record EmployeeResponse(Long id, String name, LocalDateTime createdAt) {

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getName(), employee.getCreatedAt());
    }
}
