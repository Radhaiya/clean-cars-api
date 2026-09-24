package com.example.cleancarsapi.dto;

import com.example.cleancarsapi.entity.Employee;
import com.example.cleancarsapi.entity.User;
import com.example.cleancarsapi.entity.UserRole;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An {@code employees} row as JSON. {@code user} is the linked app account —
 * present only once an invited person accepted ({@code employees.user_id}):
 * the "Users" screen shows the roster with the signed-in state per worker.
 */
public record EmployeeResponse(
        UUID id,
        String name,
        String email,
        UserBrief user,
        LocalDateTime createdAt
) {
    /** The linked account's essentials (null while the employee has no account yet). */
    public record UserBrief(
            UUID id,
            String name,
            String email,
            UserRole role
    ) {
        public static UserBrief fromUser(User user) {
            return new UserBrief(user.getId(), user.getName(), user.getEmail(), user.getRole());
        }
    }

    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getName(), employee.getEmail(),
                null, employee.getCreatedAt());
    }

    public static EmployeeResponse from(Employee employee, User linkedUser) {
        return new EmployeeResponse(employee.getId(), employee.getName(), employee.getEmail(),
                linkedUser == null ? null : UserBrief.fromUser(linkedUser), employee.getCreatedAt());
    }
}
